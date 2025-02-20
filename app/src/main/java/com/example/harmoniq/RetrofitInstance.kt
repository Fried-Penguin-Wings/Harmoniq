package com.example.harmoniq

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private const val TRANSLATE_BASE_URL = "https://translation.googleapis.com/"
    private const val TTS_BASE_URL = "https://api.elevenlabs.io/"

    val translateApi: TranslateApi by lazy {
        retrofit2.Retrofit.Builder()
            .baseUrl(TRANSLATE_BASE_URL)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(TranslateApi::class.java)
    }

    val ttsApi: TTSApi by lazy {
        retrofit2.Retrofit.Builder()
            .baseUrl(TTS_BASE_URL)
            .build()
            .create(TTSApi::class.java)
    }
}
