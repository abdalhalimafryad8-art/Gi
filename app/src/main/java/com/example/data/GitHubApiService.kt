package com.example.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface GitHubApiService {

    @GET("user")
    @Headers("Accept: application/vnd.github+json")
    suspend fun getUser(
        @Header("Authorization") token: String
    ): GitHubUser

    @POST("user/repos")
    @Headers("Accept: application/vnd.github+json")
    suspend fun createRepository(
        @Header("Authorization") token: String,
        @Body request: CreateRepoRequest
    ): CreateRepoResponse

    @PUT("repos/{owner}/{repo}/contents/{path}")
    @Headers("Accept: application/vnd.github+json")
    suspend fun uploadFile(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body request: UploadFileRequest
    ): Any
}
