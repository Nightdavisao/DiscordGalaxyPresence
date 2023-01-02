package io.github.nightdavisao.discordgalaxypresence.data

import io.github.nightdavisao.discordgalaxypresence.discord.DiscordUtils
import io.github.nightdavisao.discordgalaxypresence.data.model.LoggedInUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource {

    suspend fun login(username: String, password: String, captchaKey: String? = null): Result<LoggedInUser> {
        try {
            val token = withContext(Dispatchers.IO) {
                DiscordUtils.getTokenWithCredentials(username, password, captchaKey)
            }
            if (token != null) {
                // TODO: change that to an actual display name
                val user = LoggedInUser(token, "user")
                return Result.Success(user)
            }
            return Result.Error(Exception("token is NULL"))
        } catch (e: DiscordUtils.CaptchaException) {
            return Result.Error(e)
        } catch (e: DiscordUtils.MFAException) {
            return Result.Error(e)
        } catch (e: Throwable) {
            return Result.Error(IOException("Error logging in", e))
        }
    }

    fun mfaLogin(code: String, ticket: String): Result<LoggedInUser> {
        try {
            val token = DiscordUtils.getTokenWithTotp(code, ticket)
            if (token != null) {
                // TODO: change that to an actual display name
                val user = LoggedInUser(token, "user")
                return Result.Success(user)
            }
            return Result.Error(Exception("token is NULL"))
        } catch (e: Throwable) {
            return Result.Error(IOException("Error logging in", e))
        }
    }

    fun logout() {
        // TODO: revoke authentication
    }
}