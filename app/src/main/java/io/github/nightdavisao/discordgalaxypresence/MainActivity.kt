package io.github.nightdavisao.discordgalaxypresence

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.topjohnwu.superuser.Shell
import io.github.nightdavisao.discordgalaxypresence.ui.login.LoginActivity

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    init {
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(Shell.Builder.create()
            .setFlags(Shell.FLAG_REDIRECT_STDERR)
            .setTimeout(10)
        )
    }

    private var preferencesManager: SharedPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferenceManager.getDefaultSharedPreferences(this)

        Shell.getShell {
            setContentView(R.layout.main_activity)
            if (savedInstanceState == null) {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, SettingsFragment())
                    .commit()
            }
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val loginWithPreferences = findPreference<Preference>("login_with_credentials")
            loginWithPreferences?.setOnPreferenceClickListener {
                val intent = Intent(this.context, LoginActivity::class.java)
                startActivity(intent)
                return@setOnPreferenceClickListener false
            }
        }
    }
}