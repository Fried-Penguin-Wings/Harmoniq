package com.example.harmoniq

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private const val OPENAI_BASE_URL = "https://api.openai.com/v1/"
    private const val TTS_BASE_URL = "https://api.elevenlabs.io/"

    private val client = OkHttpClient.Builder().build()

    val openAITranslateApi: OpenAITranslateApi by lazy {
        Retrofit.Builder()
            .baseUrl(OPENAI_BASE_URL) // OpenAI API base URL
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(OpenAITranslateApi::class.java)
    }

    val ttsApi: TTSApi by lazy {
        Retrofit.Builder()
            .baseUrl(TTS_BASE_URL)
            .build()
            .create(TTSApi::class.java)
    }
}
