import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface TTSApi {
    @POST("v1/text-to-speech/4AZCG1BRUah8dBIuD0Ik") // ✅ Correct Amos Voice ID
    suspend fun textToSpeech(
        @Header("xi-api-key") apiKey: String,
        @Body requestBody: RequestBody
    ): Response<ResponseBody>
}
