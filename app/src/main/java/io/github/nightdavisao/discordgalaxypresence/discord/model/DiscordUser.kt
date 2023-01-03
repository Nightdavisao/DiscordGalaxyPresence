package io.github.nightdavisao.discordgalaxypresence.discord.model

data class DiscordUser(
    val id: Long,
    val username: String,
    val discriminator: String
)