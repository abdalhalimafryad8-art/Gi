package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUser(
    val login: String,
    val id: Long,
    @Json(name = "avatar_url") val avatarUrl: String?,
    val name: String?,
    @Json(name = "public_repos") val publicRepos: Int,
    val email: String?
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequest(
    val name: String,
    val description: String?,
    val private: Boolean,
    @Json(name = "auto_init") val autoInit: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CreateRepoResponse(
    val name: String,
    val owner: RepoOwner,
    @Json(name = "html_url") val htmlUrl: String
)

@JsonClass(generateAdapter = true)
data class RepoOwner(
    val login: String
)

@JsonClass(generateAdapter = true)
data class UploadFileRequest(
    val message: String,
    val content: String, // Base64 encoded file content
    val branch: String = "main"
)
