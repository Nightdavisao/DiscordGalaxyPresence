package io.github.nightdavisao.discordgalaxypresence.ui.login

/**
 * Authentication result : success (user details) or error message.
 */
data class LoginResult(
    val success: String? = null,
    val error: Int? = null,
    val exception: Exception? = null
)