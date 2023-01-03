package io.github.nightdavisao.discordgalaxypresence.data

/**
 * Class that requests authentication and user information from the remote data source and
 * maintains an in-memory cache of login status and user credentials information.
 */

class LoginRepository(val dataSource: LoginDataSource) {
    suspend fun login(username: String, password: String): Result<String> {
        return dataSource.login(username, password)
    }
}