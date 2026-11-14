package dev.lumenotes.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.lumenotes.app.databinding.ActivitySettingsBinding
import dev.lumenotes.settings.SettingsStore
import dev.lumenotes.settings.ThemeChoice
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    @Inject lateinit var settings: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySafeInsets(window, binding.root)
        binding.settingsToolbar.setNavigationButtonAsBack()
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_content, SettingsFragment())
                .commit()
        }
    }
}

class SettingsFragment : PreferenceFragmentCompat() {
    private val host: SettingsActivity get() = requireActivity() as SettingsActivity

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
            val choice = runCatching { ThemeChoice.valueOf(newValue as String) }.getOrNull()
            if (choice == null) {
                false
            } else {
                lifecycleScope.launch {
                    try {
                        host.settings.setTheme(choice)
                    } catch (
                        cancelled: CancellationException
                    ) {
                        throw cancelled
                    } catch (
                        _: Exception
                    ) {
                        Toast.makeText(requireContext(), R.string.action_failed, Toast.LENGTH_LONG).show()
                    }
                }
                true
            }
        }
        findPreference<Preference>("privacy")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext()).setTitle(R.string.privacy)
                .setMessage(R.string.privacy_text).setPositiveButton(R.string.close, null).show()
            true
        }
        findPreference<Preference>("licenses")?.setOnPreferenceClickListener {
            showLicenses()
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                host.settings.theme.collect { choice -> findPreference<ListPreference>("theme")?.value = choice.name }
            }
        }
    }

    private fun showLicenses() {
        val text = buildString {
            append(getString(R.string.licenses_intro))
            listOf(
                "NOTICE.txt",
                "Lume-MIT.txt",
                "OneUIProject-MIT.txt",
                "Apache-2.0.txt",
                "SQLite.txt",
                "DejaVu.txt",
                "JUnit-EPL-1.0.txt",
                "Hamcrest-BSD-3-Clause.txt"
            ).forEach { file ->
                append("\n\n—— ").append(file).append(" ——\n\n")
                append(requireContext().assets.open("licenses/$file").bufferedReader().use { it.readText() })
            }
        }
        AlertDialog.Builder(requireContext()).setTitle(R.string.licenses).setMessage(text)
            .setPositiveButton(R.string.close, null).show()
    }
}
