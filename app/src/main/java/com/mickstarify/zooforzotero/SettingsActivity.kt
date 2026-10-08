package com.mickstarify.zooforzotero

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.mickstarify.zooforzotero.LibraryActivity.WebDAV.WebDAVSetup
import com.mickstarify.zooforzotero.ZoteroStorage.AttachmentStorageManager
import com.mickstarify.zooforzotero.ZoteroStorage.STORAGE_ACCESS_REQUEST
import com.mickstarify.zooforzotero.search.FullTextDownloader
import com.mickstarify.zooforzotero.thumbnails.CoverDownloader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    @Inject
    lateinit var myStorageManager: AttachmentStorageManager

    @Inject
    lateinit var myPreferenceManager: PreferenceManager

    @Inject
    lateinit var fullTextDownloader: FullTextDownloader

    @Inject
    lateinit var coverDownloader: CoverDownloader

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        
        setupToolbar()
        
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    fun openStoragePicker() {
        myStorageManager.askUserForPath(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("zotero", "got back result request=$requestCode result=$resultCode data=$data")
        when (requestCode) {
            STORAGE_ACCESS_REQUEST -> {
                myStorageManager.setStorage(data?.dataString)
            }
        }
    }
    class SettingsFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)


            val httpTimeoutPreference: EditTextPreference? = findPreference("http_write_timeout")

            httpTimeoutPreference?.setOnBindEditTextListener { editText ->
                // Ensure the input allows only integers
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }

            httpTimeoutPreference?.setOnPreferenceChangeListener { _, newValue ->
                // Validate the input to ensure it's a non-negative integer
                val value = newValue.toString()
                val isValid = value.matches(Regex("\\d+")) // Match only non-negative integers
                if (!isValid) {
                    Toast.makeText(context, "Please enter a valid non-negative integer", Toast.LENGTH_SHORT).show()
                }
                isValid
            }

        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            // Live progress of "Download text of all PDFs" under the button.
            val downloader = (requireActivity() as SettingsActivity).fullTextDownloader
            downloader.status.observe(viewLifecycleOwner) { message ->
                findPreference<Preference>("download_all_pdf_text")?.summary = message
            }
            // Same for "Make covers for all PDFs".
            (requireActivity() as SettingsActivity).coverDownloader.status.observe(viewLifecycleOwner) { message ->
                findPreference<Preference>("make_all_covers")?.summary = message
            }
        }

        /** "12 searches saved" under the Clear search history button. */
        private fun updateSearchHistorySummary() {
            val prefs = (requireActivity() as SettingsActivity).myPreferenceManager
            findPreference<Preference>("clear_search_history")?.summary =
                com.mickstarify.zooforzotero.search.SearchHistory.countLabel(prefs.getSearchHistory().size)
        }

        override fun onResume() {
            super.onResume()
            updateSearchHistorySummary()
            // Set up a listener whenever a key changes
            preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)

            this.findPreference<SwitchPreference>("use_webdav")?.isChecked =
                preferenceManager.sharedPreferences!!.getBoolean("use_webdav", false)
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            if (preference.key == "make_all_covers") {
                val activity = requireActivity() as SettingsActivity
                val covers = activity.coverDownloader
                if (covers.isRunning || activity.myStorageManager.isAttachmentFolderOnDevice()) {
                    covers.toggle()   // stop, or start with PDFs on the phone (no download)
                } else {
                    // Cloud folder: every PDF is fetched through the cloud app. Ask first.
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Make covers for all PDFs?")
                        .setMessage(
                            "Your attachment folder is in a cloud app (such as pCloud). To draw each " +
                                "cover, the cloud app fetches that PDF, so this may download most of your " +
                                "library to the phone. It runs on Wi-Fi only, and you can stop it any time."
                        )
                        .setPositiveButton("Start") { _, _ -> covers.toggle() }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                return true
            } else if (preference.key == "clear_search_history") {
                val prefs = (requireActivity() as SettingsActivity).myPreferenceManager
                val count = prefs.getSearchHistory().size
                if (count == 0) return true
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Clear search history?")
                    .setMessage("Your $count saved searches will be removed from this phone.")
                    .setPositiveButton("Clear") { _, _ ->
                        prefs.setSearchHistory(emptyList())
                        updateSearchHistorySummary()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return true
            } else if (preference.key == "download_all_pdf_text") {
                (requireActivity() as SettingsActivity).fullTextDownloader.toggle()
                return true
            } else if (preference.key == "configure_webdav") {
                val intent = Intent(requireContext(), WebDAVSetup::class.java)
                startActivity(intent)
                return true
            } else {
                return super.onPreferenceTreeClick(preference)
            }
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            Log.d("zotero", "sharedpreference change ${key}")
            when (key) {
                "attachment_sync_location" -> {
                    if (sharedPreferences?.getString(
                            "attachment_sync_location",
                            "null"
                        ) == "CUSTOM"
                    ) {
                        (this.activity as SettingsActivity).openStoragePicker()
                    }
                }
                "use_webdav" -> {
                    if (sharedPreferences?.getBoolean("use_webdav", false) == true && !(requireActivity() as SettingsActivity).myPreferenceManager.isWebDAVConfigured()) {
                        Toast.makeText(requireContext(), "Please configure webdav first", Toast.LENGTH_SHORT).show()
                        sharedPreferences.edit().putBoolean("use_webdav", false).apply()
                        (requireActivity() as SettingsActivity).updateWebDAVPreferenceUI(false)
                    }
                }
            }
        }
    }

    private fun updateWebDAVPreferenceUI(flag: Boolean) {
        val fragment = supportFragmentManager.findFragmentById(R.id.settings) as SettingsFragment
        fragment.findPreference<SwitchPreference>("use_webdav")?.isChecked = flag
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    }
}