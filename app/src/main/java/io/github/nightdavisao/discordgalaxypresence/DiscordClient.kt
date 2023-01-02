package io.github.nightdavisao.discordgalaxypresence

import android.util.Log
import com.grack.nanojson.JsonWriter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.concurrent.thread

class DiscordClient(
    private val authorizationToken: String
) {
    companion object {
        const val TAG = "DiscordClient"
    }

    private val client = OkHttpClient()

    fun isUserTokenValid(): Boolean {
        return authorizationToken.isNotBlank()
    }

    fun sendGalaxyPresence(packageName: String, status: String) {
        thread {
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
        }
    }
}