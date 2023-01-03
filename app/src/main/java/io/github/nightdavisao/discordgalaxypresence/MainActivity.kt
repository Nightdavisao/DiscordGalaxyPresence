package io.github.nightdavisao.discordgalaxypresence

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
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
        private var accessibilityPreference: Preference? = null
        private var usageAccessPermission: Preference? = null
        private var rootPermission: Preference? = null

        private fun isUsageAccessGranted(): Boolean {
            val packageManager: PackageManager = requireContext().packageManager
            val applicationInfo = packageManager.getApplicationInfo(requireContext().packageName, 0)
            val appOpsManager = requireContext().getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    applicationInfo.uid,
                    applicationInfo.packageName
                )
            } else {
                appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    applicationInfo.uid,
                    applicationInfo.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        private fun isAccessibilityServiceEnabled(): Boolean {
            val am = requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            for (enabledService in enabledServices) {
                val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
                if (enabledServiceInfo.packageName == requireContext().packageName
                    && enabledServiceInfo.name == GameDetectionService::class.java.name) return true
            }
            return false
        }
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val loginWithPreferences = findPreference<Preference>("login_with_credentials")
            accessibilityPreference = findPreference("accessibility_permission")
            usageAccessPermission = findPreference("usage_access_permission")
            rootPermission = findPreference("root_permission")
            val miuiDisclaimer = findPreference<PreferenceCategory>("miui_prefdisclaimer")

            loginWithPreferences?.setOnPreferenceClickListener {
                val intent = Intent(this.context, LoginActivity::class.java)
                startActivity(intent)
                return@setOnPreferenceClickListener false
            }
            if (DeviceUtils.isMiUi()) {
                miuiDisclaimer?.isVisible = true
            }
            updatePermissionsSummary()
        }

        private fun updatePermissionsSummary() {
            accessibilityPreference?.summary = if (isAccessibilityServiceEnabled()) {
                accessibilityPreference?.setOnPreferenceClickListener {
                    false
                }

                getString(R.string.accessibility_permission_granted)
            } else {
                accessibilityPreference?.setOnPreferenceClickListener {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    true
                }

                getString(R.string.accessibility_permission_denied)
            }
            usageAccessPermission?.summary = if (isUsageAccessGranted()) {
                usageAccessPermission?.setOnPreferenceClickListener {
                    false
                }

                getString(R.string.usage_access_permission_granted)
            } else {
                usageAccessPermission?.setOnPreferenceClickListener {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    startActivity(intent)
                    true
                }

                getString(R.string.usage_access_permission_denied)
            }
            rootPermission?.summary = if (Shell.isAppGrantedRoot() == true) {
                getString(R.string.root_permission_granted)
            } else {
                getString(R.string.root_permission_denied)
            }
        }

        override fun onResume() {
            super.onResume()
            updatePermissionsSummary()
        }
    }
}