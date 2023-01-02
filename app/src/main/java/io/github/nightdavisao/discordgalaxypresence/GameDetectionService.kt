package io.github.nightdavisao.discordgalaxypresence

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.usage.UsageEvents.Event
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonWriter
import com.topjohnwu.superuser.Shell
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class GameDetectionService: AccessibilityService() {
    companion object {
        const val TAG = "GameDetectionService"
        // don't trust the IDE. this regex is actually all right, the character escape ISN'T redundant. don't mess with it
        val TOP_APP_REGEX = "topApp=ActivityRecord\\{(.*)\\}".toRegex()
    }
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    private var preferencesManager: SharedPreferences? = null

    override fun onServiceConnected() {
        preferencesManager = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
        this.serviceInfo = info
    }

    private fun updateDiscordPresence(packageName: String, status: String) {
        thread {
            val authorizationToken = preferencesManager?.getString("discord_token", null)
            if (!authorizationToken.isNullOrBlank()) {
                val payload = JsonWriter.string().`object`()
                    .value("package_name", packageName)
                    .value("update", status)
                    .end()
                    .done()
                Log.d(TAG, payload)

                val request = Request.Builder()
                    .url("https://discord.com/api/v6/presences")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", authorizationToken)
                    .build()

                val response = client.newCall(request).execute()

                Log.d(TAG, "presence sent, status code: ${response.code}")
            } else {
                Log.d(TAG, "discord token is NULL or BLANK")
            }
        }
    }
    // this doesn't actually check if the application is in foreground
    // i know no better way to do this.
    private fun rootIsApplicationInForeground(packageName: String): Boolean {
        val result = Shell.cmd("dumpsys window windows").exec()
        val output = result.out.joinToString("\n")

        // we will be extracting all the open activities.
        val matchResults = TOP_APP_REGEX.findAll(output)
        return matchResults.any { it.groupValues[1].split(" ")[2].substringBefore("/") == packageName }
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

    private fun updateTaskTimer(runningGame: ApplicationInfo) {
        handler.removeCallbacksAndMessages(null)
        val delayMillis = TimeUnit.MINUTES.toMillis(1)
        val runnable = object: Runnable {
            override fun run() {
                if (isApplicationInForeground(runningGame.packageName)) {
                    Log.d(TAG, "handler.postDelayed: keeping alive our presence status")
                    updateDiscordPresence(runningGame.processName, "UPDATE")
                    handler.postDelayed(this, delayMillis)
                    return
                }

                Log.d(TAG, "stopping the presence because the last app used is not the game")
                updateDiscordPresence(runningGame.packageName, "STOP")
            }
        }
        handler.postDelayed(runnable, delayMillis)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) {
            try {
                val applicationInfo =
                    baseContext.packageManager.getApplicationInfo(event.packageName.toString(), 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (applicationInfo.category == ApplicationInfo.CATEGORY_GAME || applicationInfo.category == ApplicationInfo.FLAG_IS_GAME) {
                        Log.d(TAG, "an actual game! package name: ${event.packageName}")
                        updateDiscordPresence(applicationInfo.processName, "START")
                        updateTaskTimer(applicationInfo)
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