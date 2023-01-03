package io.github.nightdavisao.discordgalaxypresence

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.usage.UsageEvents.Event
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.os.*
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.preference.PreferenceManager
import com.topjohnwu.superuser.Shell
import io.github.nightdavisao.discordgalaxypresence.discord.DiscordClient
import java.util.concurrent.TimeUnit

class GameDetectionService : AccessibilityService() {
    companion object {
        const val TAG = "GameDetectionService"
        val IS_ON_SCREEN_REGEX = "isOnScreen=(true|false)".toRegex()
        var instance: GameDetectionService? = null
    }

    private val handler = Handler(Looper.getMainLooper())

    private var preferencesManager: SharedPreferences? = null
    private var discordClient: DiscordClient? = null


    fun changeDiscordToken(token: String) {
        discordClient = DiscordClient(token)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // ensure we have shell access
        Shell.getShell()
        preferencesManager = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val userToken = preferencesManager?.getString("discord_token", null)
        if (userToken != null) {
            discordClient = DiscordClient(userToken)
        }

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
        this.serviceInfo = info
    }


    private fun rootIsApplicationInForeground(packageName: String): Boolean {
        val result = Shell.cmd("dumpsys window windows").exec()
        val output = result.out

        var isInWindow = false
        for (line in output) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Window #")) {
                if (!isInWindow) {
                    val substring = trimmed.substringBefore("/").substringAfterLast(" ")
                    if (substring == packageName) {
                        isInWindow = true
                    }
                } else {
                    isInWindow = false
                }
            }
            if (isInWindow) {
                val match = IS_ON_SCREEN_REGEX.find(trimmed)?.groupValues?.get(1)
                if (match != null) return match == "true"
            }
        }
        return false
    }

    private fun isApplicationInForeground(packageName: String): Boolean {
        return if (Shell.isAppGrantedRoot() == true) {
            Log.d(TAG, "isApplicationInForeground: using root")
            rootIsApplicationInForeground(packageName)
        } else {
            Log.d(TAG, "isApplicationInForeground: using usage stats")
            rootlessIsApplicationInForeground(packageName)
        }
    }

    private fun rootlessIsApplicationInForeground(packageName: String): Boolean {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager?
        if (usageStatsManager != null) {
            val eventsList = mutableListOf<Event>()
            val endTime = System.currentTimeMillis() + 10 * 1000
            val startTime = endTime - 1000 * 30
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                eventsList.add(event)
            }
            // after collecting events
            val lastResumedEvent = eventsList.lastOrNull {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.eventType == Event.ACTIVITY_RESUMED
                } else {
                    it.eventType == Event.MOVE_TO_FOREGROUND
                }
            }
            return lastResumedEvent?.packageName == packageName || lastResumedEvent == null
        }
        return false
    }

    private fun queuePresenceUpdate(runningGame: ApplicationInfo) {
        handler.removeCallbacksAndMessages(null)
        val delayMillis = TimeUnit.MINUTES.toMillis(1)
        val runnable = object : Runnable {
            override fun run() {
                if (isApplicationInForeground(runningGame.packageName)) {
                    Log.d(TAG, "queuePresenceUpdate: keeping alive our presence status")
                    discordClient?.sendGalaxyPresence(runningGame.processName, "UPDATE") { code ->
                        if (code == DiscordClient.NO_CONTENT) {
                            handler.postDelayed(this, delayMillis)
                        }
                    }
                    return
                }

                Log.d(TAG, "queuePresenceUpdate: stopping the presence...")
                discordClient?.sendGalaxyPresence(runningGame.packageName, "STOP")
            }
        }
        handler.postDelayed(runnable, delayMillis)
    }

    private fun isPackageAGame(packageName: String): Boolean {
        val applicationInfo = baseContext.packageManager.getApplicationInfo(packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationInfo.category == ApplicationInfo.CATEGORY_GAME
        } else {
            applicationInfo.flags.and(ApplicationInfo.FLAG_IS_GAME) == ApplicationInfo.FLAG_IS_GAME
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) {
            try {
                val applicationInfo =
                    baseContext.packageManager.getApplicationInfo(event.packageName.toString(), 0)
                if (isPackageAGame(event.packageName.toString())) {
                    Log.d(TAG, "an actual game! package name: ${event.packageName}")
                    discordClient?.sendGalaxyPresence(applicationInfo.processName, "START") { code ->
                        if (code == DiscordClient.NO_CONTENT) {
                            queuePresenceUpdate(applicationInfo)
                        } else {
                            Log.d(TAG, "onAccessibilityEvent: Discord responded with an unexpected status code")
                        }
                    }
                }
            } catch (e: NameNotFoundException) {
                Log.d(TAG, "failed to query package")
            }
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }
}