package com.example.harmoniq

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface TTSApi {
    @POST("v1/text-to-speech/{voiceId}")
    suspend fun textToSpeech(
        @Path("voiceId") voiceId: String,
        @Header("xi-api-key") apiKey: String,
        @Body requestBody: RequestBody
    ): Response<ResponseBody>
}