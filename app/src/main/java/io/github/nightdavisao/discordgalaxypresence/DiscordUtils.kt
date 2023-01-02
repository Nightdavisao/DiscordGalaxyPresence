package io.github.nightdavisao.discordgalaxypresence

import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody


object DiscordUtils {
    class CaptchaException(siteKey: String): Exception()
    class MFAException(ticket: String): Exception()
    class EmptyBodyException: Exception()
    class UnexpectedStatusCode(code: Int): Exception()

    private val client = OkHttpClient()

    fun getTokenWithCredentials(login: String, password: String, captchaKey: String?): String? {
        val payload = JsonWriter.string().`object`()
            .value("captcha_key", captchaKey)
            .value("gift_code_sku_id")
            .value("login", login)
            .value("login_source")
            .value("password", password)
            .value("undelete", false) // ?
            .end()
            .done()

        val request = client.newCall(Request.Builder()
            .url("https://discord.com/api/v9/auth/login")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build())

        val response = request.execute()

        if (response.body != null) {
            val body = JsonParser.`object`().from(response.body!!.string())

            if (response.code == 200) {
                if (body.isNull("token") && body.getBoolean("mfa") && body.isString("ticket")) {
                    throw MFAException(body.getString("ticket"))
                } else {
                    // unlikely to reach this easy to the token
                    return body.getString("token")
                }
            } else if (response.code == 400) {
                if (body.isString("captcha_sitekey"))  {
                    throw CaptchaException(body.getString("captcha_sitekey"))
                }
            } else {
                throw UnexpectedStatusCode(response.code)
            }
        } else {
            throw EmptyBodyException()
        }
        return null
    }

    fun getTokenWithTotp(code: String, ticket: String): String? {
        val payload = JsonWriter.string().`object`()
            .value("code", code)
            .value("gift_code_sku_id")
            .value("login_source")
            .value("ticket", ticket)
            .end()
            .done()

        val request = client.newCall(Request.Builder()
            .url("https://discord.com/api/v9/auth/mfa/totp")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build())

        val response = request.execute()

        if (response.code == 200) {
            val body = response.body
            if (body != null) {
                return JsonParser.`object`().from(body.string()).getString("token")
            } else {
                throw EmptyBodyException()
            }
        }
        return null
    }
}