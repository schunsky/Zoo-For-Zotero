package com.mickstarify.zooforzotero.LibraryActivity.ViewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mickstarify.zooforzotero.LibraryActivity.ListEntry
import com.mickstarify.zooforzotero.ZoteroStorage.Database.Collection
import com.mickstarify.zooforzotero.ZoteroStorage.Database.Item
import com.mickstarify.zooforzotero.search.SearchProgress

class LibraryListViewModel : ViewModel() {
    private val items = MutableLiveData<List<ListEntry>>()

    fun getItems(): LiveData<List<ListEntry>> = items
    fun setItems(items: List<ListEntry>) {
        Log.d("zotero", "Setting items ${items.size}")
        this.items.value = items
    }

    // Search-result context lines, by item key (empty when not searching).
    private val snippets = MutableLiveData<Map<String, CharSequence>>(emptyMap())
    fun getSnippets(): LiveData<Map<String, CharSequence>> = snippets
    fun setSnippets(value: Map<String, CharSequence>) {
        snippets.value = value
    }

    // Search inside PDFs: progress window state (null = not searching).
    private val searchProgress = MutableLiveData<SearchProgress?>(null)
    fun getSearchProgress(): LiveData<SearchProgress?> = searchProgress
    fun setSearchProgress(value: SearchProgress?) {
        searchProgress.value = value
    }

    // Line under the title after a search, e.g. "23 results · matches in 12 PDFs" (null = none).
    private val searchSummary = MutableLiveData<String?>(null)
    fun getSearchSummary(): LiveData<String?> = searchSummary
    fun setSearchSummary(value: String?) {
        searchSummary.value = value
    }

    // Cancel pressed in the progress window (counter, so every press is seen).
    private val searchCancelRequests = MutableLiveData<Int>()
    fun getSearchCancelRequests(): LiveData<Int> = searchCancelRequests
    fun requestSearchCancel() {
        searchCancelRequests.value = (searchCancelRequests.value ?: 0) + 1
    }

    // Ask the screen to empty and close the search box.
    private val clearSearchBoxRequests = MutableLiveData<Int>()
    fun getClearSearchBoxRequests(): LiveData<Int> = clearSearchBoxRequests
    fun requestClearSearchBox() {
        clearSearchBoxRequests.value = (clearSearchBoxRequests.value ?: 0) + 1
    }

    private val itemClicked = MutableLiveData<Item>()
    fun getOnItemClicked(): LiveData<Item> = itemClicked
    fun onItemClicked(item: Item) {
        this.itemClicked.value = item
    }

    private val attachmentClicked = MutableLiveData<Item>()
    fun getOnAttachmentClicked(): LiveData<Item> = attachmentClicked
    fun onAttachmentClicked(attachment: Item) {
        this.attachmentClicked.value = attachment
    }

    private val collectionClicked = MutableLiveData<Collection>()
    fun getOnCollectionClicked(): LiveData<Collection> = collectionClicked
    fun onCollectionClicked(collection: Collection) {
        this.collectionClicked.value = collection
    }

    private val scannedBarcode = MutableLiveData<String>()
    fun scannedBarcodeNumber(barcodeNo: String) {
        scannedBarcode.value = barcodeNo
    }

    fun getScannedBarcode(): LiveData<String> = scannedBarcode

    private val isShowingLoadingAnimation = MutableLiveData<Boolean>(false)
    fun setIsShowingLoadingAnimation(value: Boolean) {
        if (isShowingLoadingAnimation.value != value) {
            isShowingLoadingAnimation.value = value
        }
    }

    fun getIsShowingLoadingAnimation(): LiveData<Boolean> = isShowingLoadingAnimation

    private val onLibraryRefreshRequested = MutableLiveData<Int>()
    fun onLibraryRefreshRequested() {
        // changes the value so any listener will get pinged.
        onLibraryRefreshRequested.value = (onLibraryRefreshRequested.value ?: 0) + 1
    }

    fun getOnLibraryRefreshRequested(): LiveData<Int> = onLibraryRefreshRequested

    private val libraryFilterText = MutableLiveData<String>("")
    fun getLibraryFilterText(): LiveData<String> = libraryFilterText
    fun setLibraryFilterText(query: String) {
        if (this.libraryFilterText.value != query) {
            this.libraryFilterText.value = query
        }
    }

    private val _onBarcodeScanButtonPressed = MutableLiveData<Boolean>(false)
    fun onBarcodeScanButtonPressed() {
        Log.d("zotero", "barcode scan button pressed")
        _onBarcodeScanButtonPressed.value = true
        _onBarcodeScanButtonPressed.value = false
    }
    fun getOnBarcodeScanButtonPressed(): LiveData<Boolean> = _onBarcodeScanButtonPressed
}