package com.awd.driverouter.data.remote

import retrofit2.http.GET

data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String,
    val html_url: String
)

interface GitHubUpdateService {
    @GET("repos/putuwahyu29/awd-driverouter-android/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
}
