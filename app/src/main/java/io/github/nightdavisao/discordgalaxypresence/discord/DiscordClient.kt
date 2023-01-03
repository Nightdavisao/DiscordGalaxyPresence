package io.github.nightdavisao.discordgalaxypresence.discord

import android.util.Log
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonReader
import com.grack.nanojson.JsonWriter
import io.github.nightdavisao.discordgalaxypresence.discord.model.DiscordUser
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.concurrent.thread

class DiscordClient(
    private val authorizationToken: String
) {
    companion object {
        const val TAG = "DiscordClient"
        const val V9_BASE_URL = "https://discord.com/api/v6"
        const val NO_CONTENT = 204
        const val NOT_FOUND = 404
    }

    class AuthInterceptor(private val authorizationToken: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val newRequest = request.newBuilder()
                .addHeader("Authorization", authorizationToken)
                .build()
            return chain.proceed(newRequest)
        }
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(authorizationToken))
        .build()

    fun isUserTokenValid(): Boolean {
        return authorizationToken.isNotBlank()
    }

    fun getUserTag(): String? {
        val user = getUser()
        if (user != null) {
            return "${user.username}#${user.discriminator}"
        }
        return null
    }

    private fun getUser(): DiscordUser? {
        val request = Request.Builder()
            .url("$V9_BASE_URL/users/@me")
            .build()

        val response = client.newCall(request).execute()

        return if (response.body != null) {
            val jsonParser = JsonParser.`object`().from(response.body!!.string())
            DiscordUser(
                jsonParser.getLong("id"),
                jsonParser.getString("username"),
                jsonParser.getString("discriminator")
            )
        } else {
            null
        }
    }

    fun sendGalaxyPresence(packageName: String, status: String, callback: ((code: Int?) -> Unit)? = null) {
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
                .build()

            val response = client.newCall(request).execute()

            Log.d(TAG, "presence sent, status code: ${response.code}")
            callback?.invoke(response.code)
        }
    }
}