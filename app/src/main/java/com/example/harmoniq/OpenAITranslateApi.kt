package com.example.harmoniq

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface OpenAITranslateApi {
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun translate(
        @Header("Authorization") authHeader: String,
        @Body requestBody: RequestBody
    ): Response<ResponseBody>
}
