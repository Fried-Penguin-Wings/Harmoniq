package com.example.harmoniq

import retrofit2.http.GET
import retrofit2.http.Query

interface TranslateApi {
    @GET("language/translate/v2")
    suspend fun translateText(
        @Query("q") text: String,  // The text to translate
        @Query("source") sourceLang: String = "auto",  // Auto-detect source if not provided
        @Query("target") targetLanguage: String,  // Required: Target language
        @Query("key") apiKey: String  // Your Google Translate API key
    ): TranslationResponse
}


// Data models for API response
data class TranslationResponse(val data: Data)
data class Data(val translations: List<Translation>)
data class Translation(val translatedText: String)
