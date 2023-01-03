package io.github.nightdavisao.discordgalaxypresence.data

import io.github.nightdavisao.discordgalaxypresence.discord.DiscordUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource {
    suspend fun login(username: String, password: String, captchaKey: String? = null): Result<String> {
        try {
            val token = withContext(Dispatchers.IO) {
                DiscordUtils.getTokenWithCredentials(username, password, captchaKey)
            }
            if (token != null) {
                return Result.Success(token)
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
}