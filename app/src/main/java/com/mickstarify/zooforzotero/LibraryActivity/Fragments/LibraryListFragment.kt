package com.mickstarify.zooforzotero.LibraryActivity.Fragments

import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.getbase.floatingactionbutton.FloatingActionButton
import com.getbase.floatingactionbutton.FloatingActionsMenu
import com.mickstarify.zooforzotero.LibraryActivity.LibraryActivity
import com.mickstarify.zooforzotero.LibraryActivity.ViewModels.LibraryListViewModel
import com.mickstarify.zooforzotero.PreferenceManager
import com.mickstarify.zooforzotero.R
import com.mickstarify.zooforzotero.ZoteroStorage.Database.Collection
import com.mickstarify.zooforzotero.ZoteroStorage.Database.Item
import com.mickstarify.zooforzotero.adapters.LibraryListInteractionListener
import com.mickstarify.zooforzotero.adapters.LibraryListRecyclerViewAdapter
import com.mickstarify.zooforzotero.thumbnails.PdfThumbnailLoader
import com.mickstarify.zooforzotero.search.SearchProgress
import com.mickstarify.zooforzotero.search.LibraryViewMode
import android.view.inputmethod.InputMethodManager
import android.view.KeyEvent
import android.widget.ProgressBar
import android.widget.TextView
import com.mickstarify.zooforzotero.thumbnails.ThumbnailSizing
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LibraryListFragment : Fragment(), LibraryListInteractionListener,
    SwipeRefreshLayout.OnRefreshListener {

    companion object {
        fun newInstance() = LibraryListFragment()
    }

    private lateinit var viewModel: LibraryListViewModel

    private lateinit var fab: FloatingActionsMenu

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var pdfThumbnailLoader: PdfThumbnailLoader

    private var listAdapter: LibraryListRecyclerViewAdapter? = null

    // Progress window for searches inside PDFs.
    private var progressDialog: androidx.appcompat.app.AlertDialog? = null
    private var searchMenuItem: MenuItem? = null
    private var viewToggleMenuItem: MenuItem? = null

    /** Is the search box open? Search results always use the list view. */
    private var searchBoxOpen = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.library_list_fragment, container, false)
    }

    fun hideKeyboard(activity: Activity) {
        val imm: InputMethodManager =
            activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        //Find the currently focused view, so we can grab the correct window token from it.
        var view = activity.currentFocus
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = View(activity)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.activity_library_actionbar, menu)
        super.onCreateOptionsMenu(menu, inflater)
        viewToggleMenuItem = menu.findItem(R.id.toggle_gallery_view)
        searchBoxOpen = false   // a freshly built menu starts with the search box closed
        updateViewToggle(viewToggleMenuItem)

        val menuItem = menu.findItem(R.id.search)
        menuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                setSearchBoxOpen(true)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                (requireActivity() as LibraryActivity).presenter.closeQuery()
                setSearchBoxOpen(false)
                return true
            }
        })

        val searchManager =
            (requireActivity() as LibraryActivity).getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchMenuItem = menu.findItem(R.id.search)
        searchView = menu.findItem(R.id.search).actionView as SearchView
        searchView.apply {
            setSearchableInfo(searchManager.getSearchableInfo((requireActivity() as LibraryActivity).componentName))
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
//                    setTitle("Search results for $query")
                    // we will only search on submit for android 7<=
                    if (!preferenceManager.shouldLiveSearch()) {
                        viewModel.setLibraryFilterText(query)
                    }
                    (requireActivity() as LibraryActivity).presenter.addFilterState(query)
                    Log.d("zotero", "queryOnQuery $query")
                    hideKeyboard(requireActivity())
                    return true

                    // need to dismiss keyboard.
                }

                override fun onQueryTextChange(query: String): Boolean {
                    if (!preferenceManager.shouldLiveSearch()) {
                        return false
                    }
                    Log.d("zotero", "queryOnQuery $query")
                    viewModel.setLibraryFilterText(query)
                    return true
                }

            })
        }
    }

    lateinit var searchView: SearchView

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel = ViewModelProvider(requireActivity()).get(LibraryListViewModel::class.java)
        val recyclerView = requireView().findViewById<RecyclerView>(R.id.recyclerView)
        val adapter = LibraryListRecyclerViewAdapter(
            emptyList(),
            this,
            thumbnailLoader = if (preferenceManager.shouldShowPdfThumbnails()) pdfThumbnailLoader else null
        )

        recyclerView.adapter = adapter
        listAdapter = adapter
        applyViewMode()

        viewModel.getItems().observe(viewLifecycleOwner) { entries ->
            Log.d("zotero", "Loading new set of item (${entries.size} length)")
            adapter.items = entries
            adapter.notifyDataSetChanged()

            if (entries.size == 0) {
                showEmptyList()
            } else {
                hideEmptyList()
            }
        }

        val swipeRefresh =
            requireView().findViewById<SwipeRefreshLayout>(R.id.library_swipe_refresh)
        swipeRefresh.setOnRefreshListener(this)
        viewModel.getSearchProgress().observe(viewLifecycleOwner) { progress ->
            showSearchProgress(progress)
        }

        // "23 results · matches in 12 PDFs" under the title.
        viewModel.getSearchSummary().observe(viewLifecycleOwner) { summary ->
            (requireActivity() as androidx.appcompat.app.AppCompatActivity).supportActionBar?.subtitle = summary
        }

        // Act only on new requests (the last one is re-delivered when the screen is rebuilt).
        var handledClears = viewModel.getClearSearchBoxRequests().value ?: 0
        viewModel.getClearSearchBoxRequests().observe(viewLifecycleOwner) { count ->
            if (count != null && count > handledClears) {
                handledClears = count
                clearSearchBox()
            }
        }

        viewModel.getSnippets().observe(viewLifecycleOwner) { snippets ->
            listAdapter?.let { adapter ->
                adapter.snippets = snippets
                adapter.notifyDataSetChanged()
            }
        }

        viewModel.getIsShowingLoadingAnimation().observe(viewLifecycleOwner) {
            swipeRefresh.isRefreshing = it
        }

        fab = requireView().findViewById<FloatingActionsMenu>(R.id.fab_add_item)

        // Floating search button: opens the search box with the keyboard ready.
        requireView().findViewById<FloatingActionButton>(R.id.fab_search).setOnClickListener {
            fab.collapse()
            openSearchBox()
        }

        val fabZoteroSave =
            requireView().findViewById<FloatingActionButton>(R.id.fab_action_zotero_save)
        fabZoteroSave.setOnClickListener {
            val url = "https://www.zotero.org/save"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        }
        val fabISBNScanner =
            requireView().findViewById<FloatingActionButton>(R.id.fab_action_barcode_scanner)
        fabISBNScanner.setOnClickListener {
            viewModel.onBarcodeScanButtonPressed()
        }
        
        // Handle window insets for FAB positioning
        setupWindowInsets()
    }

    override fun onResume() {
        super.onResume()

        fab.collapse()

        // Back from the PDF viewer (or anywhere else): a PDF may have been
        // downloaded meanwhile, so look again and redraw the visible rows.
        listAdapter?.let { adapter ->
            if (adapter.thumbnailLoader != null) {
                pdfThumbnailLoader.forgetMissing()
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.toggle_gallery_view) {
            preferenceManager.setGalleryView(!preferenceManager.isGalleryView())
            applyViewMode()
            updateViewToggle(item)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /** Opens the toolbar search box and shows the keyboard. */
    private fun openSearchBox() {
        val item = searchMenuItem ?: return
        item.expandActionView()
        if (::searchView.isInitialized) {
            searchView.isIconified = false
            searchView.requestFocus()
            searchView.post {
                val imm = requireContext().getSystemService(InputMethodManager::class.java)
                imm?.showSoftInput(searchView.findFocus() ?: searchView, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    /** Search box opened or closed: switch list/gallery and show/hide the buttons to match. */
    private fun setSearchBoxOpen(open: Boolean) {
        searchBoxOpen = open
        view?.findViewById<View>(R.id.fab_search)?.visibility =
            if (LibraryViewMode.showSearchButton(open)) View.VISIBLE else View.GONE
        viewToggleMenuItem?.isVisible = LibraryViewMode.showViewSwitch(open)
        applyViewMode()
    }

    /**
     * Shows the list as a grid of covers or as rows: the view chosen in the toolbar,
     * except that search results always use the list. The chosen view isn't changed,
     * so gallery comes back when the search box closes.
     */
    private fun applyViewMode() {
        val recyclerView = view?.findViewById<RecyclerView>(R.id.recyclerView) ?: return
        val adapter = listAdapter ?: return
        val gallery = LibraryViewMode.showGallery(preferenceManager.isGalleryView(), searchBoxOpen)
        if (adapter.galleryMode == gallery && recyclerView.layoutManager != null) return
        adapter.galleryMode = gallery
        recyclerView.layoutManager = if (gallery) {
            GridLayoutManager(
                requireContext(),
                ThumbnailSizing.galleryColumns(resources.configuration.screenWidthDp)
            )
        } else {
            LinearLayoutManager(requireContext())
        }
        adapter.notifyDataSetChanged()
    }

    /** The toolbar button offers the *other* view: grid icon in list view, list icon in gallery. */
    private fun updateViewToggle(menuItem: MenuItem?) {
        menuItem ?: return
        if (preferenceManager.isGalleryView()) {
            menuItem.setIcon(R.drawable.ic_view_list_white_24)
            menuItem.setTitle(R.string.list_view)
        } else {
            menuItem.setIcon(R.drawable.ic_view_grid_white_24)
            menuItem.setTitle(R.string.gallery_view)
        }
    }

    /**
     * Shows, updates or closes the progress window. While it's open, the rest of the
     * screen can't be used; its Cancel button (or the back button) stops the search.
     */
    private fun showSearchProgress(progress: SearchProgress?) {
        if (progress == null) {
            progressDialog?.dismiss()
            progressDialog = null
            return
        }
        val dialog = progressDialog ?: createProgressDialog().also { progressDialog = it }
        val bar = dialog.findViewById<ProgressBar>(R.id.progressBar_search)
        val percent = progress.percent()
        if (percent == null) {
            bar?.isIndeterminate = true
        } else {
            bar?.isIndeterminate = false
            bar?.progress = percent
        }
        dialog.findViewById<TextView>(R.id.textView_search_progress_status)?.text = progress.message()
        dialog.findViewById<TextView>(R.id.textView_search_progress_matches)?.apply {
            val line = progress.matchesLine()
            visibility = if (line == null) View.GONE else View.VISIBLE
            text = line
        }
    }

    private fun createProgressDialog(): androidx.appcompat.app.AlertDialog {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Searching")
            .setView(R.layout.search_progress_dialog)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> viewModel.requestSearchCancel() }
            .setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    viewModel.requestSearchCancel()
                    true
                } else {
                    keyCode == KeyEvent.KEYCODE_BACK   // swallow the key-down too
                }
            }
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { if (progressDialog === dialog) progressDialog = null }
        dialog.show()   // views inside exist only after show()
        return dialog
    }

    /** Empties and closes the search box (after Cancel). */
    private fun clearSearchBox() {
        if (::searchView.isInitialized) {
            searchView.setQuery("", false)
            searchView.clearFocus()
        }
        searchMenuItem?.collapseActionView()
    }

    override fun onDestroyView() {
        // Avoid a leaked window; the open search re-shows it when the screen is rebuilt.
        progressDialog?.setOnDismissListener(null)
        progressDialog?.dismiss()
        progressDialog = null
        super.onDestroyView()
    }

    override fun onItemOpen(item: Item) {
        viewModel.onItemClicked(item)
    }

    override fun onCollectionOpen(collection: Collection) {
        viewModel.onCollectionClicked(collection)
    }

    override fun onItemAttachmentOpen(item: Item) {
        viewModel.onAttachmentClicked(item)
    }

    override fun onRefresh() {
        Log.d("Zotero", "got request for refresh")
        viewModel.onLibraryRefreshRequested()
    }

    fun showEmptyList() {
        val layout =
            requireView().findViewById<ConstraintLayout>(R.id.constraintLayout_library_content)
        layout.visibility = View.VISIBLE
    }

    fun hideEmptyList() {
        val layout =
            requireView().findViewById<ConstraintLayout>(R.id.constraintLayout_library_content)
        layout.visibility = View.GONE
    }
    
    private fun setupWindowInsets() {
        val rootView = requireView().findViewById<ConstraintLayout>(R.id.constraintLayout_library_view)
        
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Only apply bottom padding if there's actually a navigation bar (insets.bottom > 0)
            if (insets.bottom > 0) {
                view.updatePadding(bottom = insets.bottom)
            }
            
            windowInsets
        }
    }
}