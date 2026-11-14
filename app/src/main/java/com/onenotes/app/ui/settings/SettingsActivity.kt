package com.onenotes.app.ui.settings

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.onenotes.app.OneNotesApp
import com.onenotes.app.R
import com.onenotes.app.data.repository.SettingsRepository
import com.onenotes.app.databinding.ActivitySettingsBinding
import com.onenotes.app.ui.lock.LockActivity
import com.onenotes.app.util.shareFile
import com.onenotes.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Settings of the app.
 *
 * The screen is a plain [PreferenceFragmentCompat] filled by `res/xml/preferences.xml`: on a
 * Galaxy device the sesl fork of the preference library renders the very same rows, switches and
 * dialogs used by the system settings, which is exactly the look we want.
 *
 * The values are written into the same shared preferences read by [SettingsRepository], so every
 * other screen of the app reacts to a change immediately through its flows.
 */
class SettingsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.onenotes.app.ui.common.FontScale.apply(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }
}

class SettingsFragment : PreferenceFragmentCompat() {

    private val container by lazy { OneNotesApp.containerOf(requireContext()) }

    private val pickJson = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importBackup(uri)
    }

    private val pickSamsung = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importSamsung(uri)
    }

    private val signIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshDrive()
    }

    private var working = false
    private var lockPreference: SwitchPreferenceCompat? = null

    private val lockConfirm = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            container.settings.setAppLock(true)
            lockPreference?.isChecked = true
            requireContext().toast(R.string.msg_note_locked)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = SettingsRepository.PREFS_NAME
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>("version")?.summary = versionName()
        findPreference<Preference>("export_json")?.setOnPreferenceClickListener {
            runBackupExport()
            true
        }
        findPreference<Preference>("import_json")?.setOnPreferenceClickListener {
            pickJson.launch("application/json")
            true
        }
        findPreference<Preference>("import_samsung")?.setOnPreferenceClickListener {
            pickSamsung.launch("*/*")
            true
        }
        findPreference<Preference>("export_markdown")?.setOnPreferenceClickListener {
            exportArchive("md")
            true
        }
        findPreference<Preference>("export_pdf")?.setOnPreferenceClickListener {
            exportArchive("pdf")
            true
        }
        findPreference<Preference>("drive_signin")?.setOnPreferenceClickListener {
            toggleDrive()
            true
        }
        findPreference<Preference>("drive_backup")?.setOnPreferenceClickListener {
            backupToDrive()
            true
        }
        findPreference<Preference>("drive_restore")?.setOnPreferenceClickListener {
            restoreFromDrive()
            true
        }
        findPreference<Preference>("credits")?.setOnPreferenceClickListener { true }

        // The app lock is confirmed with the fingerprint/PIN before it is turned on, so that
        // the user cannot lock himself out by mistake.
        lockPreference = findPreference("app_lock")
        lockPreference?.setOnPreferenceChangeListener { _, value ->
            if (value == true) {
                lockConfirm.launch(
                    LockActivity.intent(
                        requireContext(),
                        LockActivity.REASON_LOCK,
                        getString(R.string.lock_app_title)
                    )
                )
                false
            } else {
                container.settings.setAppLock(false)
                true
            }
        }

        refreshDrive()
    }

    // --------------------------------------------------------------- import / export

    private fun runBackupExport() {
        if (working) return
        working = true
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { container.export.exportJson() }
            working = false
            requireContext().shareFile(file, "application/json", getString(R.string.pref_export_json_title))
        }
    }

    private fun exportArchive(format: String) {
        if (working) return
        working = true
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                when (format) {
                    "pdf" -> container.export.exportAllPdf()
                    else -> container.export.exportAllMarkdown()
                }
            }
            working = false
            requireContext().shareFile(
                file,
                if (format == "pdf") "application/pdf" else "text/markdown",
                getString(R.string.app_name)
            )
        }
    }

    private fun importBackup(uri: Uri) {
        if (working) return
        working = true
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { container.export.importJson(uri) }
            working = false
            requireContext().toast(getString(R.string.msg_import_done, count))
        }
    }

    private fun importSamsung(uri: Uri) {
        if (working) return
        working = true
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { container.export.importSamsungNotes(uri) }
            working = false
            requireContext().toast(getString(R.string.msg_import_done, count))
        }
    }

    // ----------------------------------------------------------------------- Drive

    private fun toggleDrive() {
        if (container.drive.isSignedIn()) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { container.drive.signOut() }
                container.settings.setDriveAccount("")
                container.settings.setLastBackupAt(0L)
                refreshDrive()
            }
        } else {
            signIn.launch(container.drive.signInIntent())
        }
    }

    private fun backupToDrive() {
        if (!checkSignedIn()) return
        if (working) return
        working = true
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { container.export.exportJson() }
            val id = withContext(Dispatchers.IO) { container.drive.upload(file) }
            working = false
            if (id == null) {
                requireContext().toast(R.string.msg_error_generic)
            } else {
                container.settings.setLastBackupAt(System.currentTimeMillis())
                container.settings.setDriveAccount(container.drive.accountEmail().orEmpty())
                requireContext().toast(R.string.msg_backup_done)
            }
            refreshDrive()
        }
    }

    private fun restoreFromDrive() {
        if (!checkSignedIn()) return
        if (working) return
        working = true
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val latest = container.drive.latestBackup() ?: return@withContext null
                container.drive.download(latest)
            }
            val count = if (file != null) {
                withContext(Dispatchers.IO) { container.export.importJson(Uri.fromFile(file)) }
            } else {
                0
            }
            working = false
            if (count > 0) {
                requireContext().toast(getString(R.string.msg_import_done, count))
            } else {
                requireContext().toast(R.string.msg_error_generic)
            }
        }
    }

    private fun checkSignedIn(): Boolean {
        if (!container.drive.isSignedIn()) {
            requireContext().toast(R.string.msg_sign_in_required)
            return false
        }
        return true
    }

    private fun refreshDrive() {
        val signedIn = container.drive.isSignedIn()
        val email = container.drive.accountEmail()
        findPreference<Preference>("drive_signin")?.apply {
            title = getString(
                if (signedIn) R.string.pref_drive_signout_title else R.string.pref_drive_signin_title
            )
            summary = if (signedIn) {
                email ?: getString(R.string.pref_drive_signin_summary)
            } else {
                getString(R.string.pref_drive_signin_summary)
            }
        }
        lifecycleScope.launch {
            val last = container.settings.lastBackupAt.first()
            findPreference<Preference>("drive_backup")?.summary =
                if (last > 0L) {
                    getString(
                        R.string.pref_last_backup,
                        com.onenotes.app.util.TimeUtils.dateTime(requireContext(), last)
                    )
                } else {
                    getString(R.string.pref_drive_backup_summary_none)
                }
        }
    }

    private fun versionName(): String =
        runCatching {
            val info = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            info.versionName ?: ""
        }.getOrDefault("")
}
