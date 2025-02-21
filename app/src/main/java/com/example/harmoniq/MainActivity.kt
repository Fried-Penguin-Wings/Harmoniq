package com.example.harmoniq

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import java.io.File

val curvanaFont = FontFamily(Font(R.font.curvana))

class MainActivity : ComponentActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private val mediaPlayer = MediaPlayer()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("PERMISSIONS", "Microphone permission granted")
        } else {
            Log.e("PERMISSIONS", "Microphone permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        lifecycleScope.launch {
            val openAiKey = DataStoreModule.readApiKey(applicationContext, "openai_api_key").first() as? String
            val elevenLabsKey = DataStoreModule.readApiKey(applicationContext, "eleven_labs_api_key").first() as? String
            val userVoiceId = DataStoreModule.readApiKey(applicationContext, "selected_voice").first() as? String ?: ""

            if (openAiKey.isNullOrBlank() || elevenLabsKey.isNullOrBlank()) {
                startActivity(Intent(this@MainActivity, SetupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                finish()
                return@launch
            }

            setContent {
                AppTheme(darkTheme = isSystemInDarkTheme()) {
                    AppUI(
                        context = this@MainActivity,
                        speechRecognizer = speechRecognizer,
                        openAiApiKey = openAiKey,
                        elevenLabsApiKey = elevenLabsKey,
                        voiceId = userVoiceId
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        mediaPlayer.release()
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun getMediaPlayer(): MediaPlayer = mediaPlayer
}

@Composable
fun AppTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF00BFA6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.White,
            onBackground = Color.White
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF00BFA6),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFF5F5F5),
            onPrimary = Color.White,
            onBackground = Color.Black
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}

@Composable
fun AppUI(
    context: Context,
    speechRecognizer: SpeechRecognizer,
    openAiApiKey: String,
    elevenLabsApiKey: String,
    voiceId: String
) {
    val coroutineScope = rememberCoroutineScope()
    var text by remember { mutableStateOf("Tap & Speak") }
    var isListening by remember { mutableStateOf(false) }
    var inputLanguage by remember { mutableStateOf("en") }
    var outputLanguage by remember { mutableStateOf("es") }

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                text = "Listening..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                text = "Error occurred: $error. Tap to try again."
                isListening = false
                Log.e("SPEECH", "Recognition error: $error")
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    text = recognizedText
                    translate(
                        recognizedText,
                        inputLanguage,
                        outputLanguage,
                        coroutineScope,
                        context,
                        openAiApiKey
                    ) { translatedText ->
                        text = translatedText
                        playSpeech(translatedText, context, elevenLabsApiKey, voiceId)
                    }
                } else {
                    text = "No speech recognized. Tap to try again."
                    Log.w("SPEECH", "No recognition results")
                }
                isListening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Harmoniq",
            fontFamily = curvanaFont,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            LanguageDropdown("Select Input Language", inputLanguage) {
                inputLanguage = it
                coroutineScope.launch {
                    DataStoreModule.saveApiKey(context, "input_language", it)
                    Log.d("SPEECH", "Input language updated to: $it")
                }
            }
            LanguageDropdown("Select Output Language", outputLanguage) {
                outputLanguage = it
                coroutineScope.launch {
                    DataStoreModule.saveApiKey(context, "output_language", it)
                }
            }

            FloatingActionButton(
                onClick = { inputLanguage = outputLanguage.also { outputLanguage = inputLanguage } },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(56.dp)
                    .padding(10.dp)
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Swap Languages", tint = MaterialTheme.colorScheme.onPrimary)
            }

            Button(
                onClick = {
                    if (isListening) {
                        speechRecognizer.stopListening()
                        isListening = false
                        text = "Tap & Speak"
                    } else {
                        isListening = true
                        val fullInputLanguage = when (inputLanguage) {
                            "en" -> "en-US"
                            "es" -> "es-ES"
                            "zh" -> "zh-CN"
                            "fr" -> "fr-FR"
                            "hi" -> "hi-IN"
                            "ar" -> "ar-SA"
                            "pt" -> "pt-PT"
                            "ru" -> "ru-RU"
                            "bg" -> "bg-BG"
                            "hr" -> "hr-HR"
                            "cs" -> "cs-CZ"
                            "da" -> "da-DK"
                            "nl" -> "nl-NL"
                            "tl" -> "tl-PH"
                            "fi" -> "fi-FI"
                            "de" -> "de-DE"
                            "id" -> "id-ID"
                            "it" -> "it-IT"
                            "ja" -> "ja-JP"
                            "ko" -> "ko-KR"
                            "ms" -> "ms-MY"
                            "pl" -> "pl-PL"
                            "ro" -> "ro-RO"
                            "sk" -> "sk-SK"
                            "sv" -> "sv-SE"
                            "ta" -> "ta-IN"
                            "tr" -> "tr-TR"
                            "uk" -> "uk-UA"
                            else -> "en-US"
                        }
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, fullInputLanguage)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                        }
                        Log.d("SPEECH", "Starting recognition with language: $fullInputLanguage")
                        speechRecognizer.setRecognitionListener(recognitionListener)
                        speechRecognizer.startListening(intent)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = if (isListening) "Stop Listening" else "Tap & Speak",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 18.sp,
                    fontFamily = curvanaFont
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropdown(
    label: String,
    selectedValue: String,
    onValueSelected: (String) -> Unit
) {
    val languages = mapOf(
        // Most common languages first
        "🇺🇸 English" to "en",
        "🇪🇸 Spanish" to "es",
        "🇨🇳 Chinese" to "zh",
        "🇫🇷 French" to "fr",
        "🇮🇳 Hindi" to "hi",
        "🇸🇦 Arabic" to "ar",
        "🇵🇹 Portuguese" to "pt",
        "🇷🇺 Russian" to "ru",
        // Remaining languages in alphabetical order
        "🇧🇬 Bulgarian" to "bg",
        "🇭🇷 Croatian" to "hr",
        "🇨🇿 Czech" to "cs",
        "🇩🇰 Danish" to "da",
        "🇳🇱 Dutch" to "nl",
        "🇵🇭 Filipino" to "tl",
        "🇫🇮 Finnish" to "fi",
        "🇩🇪 German" to "de",
        "🇮🇩 Indonesian" to "id",
        "🇮🇹 Italian" to "it",
        "🇯🇵 Japanese" to "ja",
        "🇰🇷 Korean" to "ko",
        "🇲🇾 Malay" to "ms",
        "🇵🇱 Polish" to "pl",
        "🇷🇴 Romanian" to "ro",
        "🇸🇰 Slovak" to "sk",
        "🇸🇪 Swedish" to "sv",
        "🇮🇳 Tamil" to "ta",
        "🇹🇷 Turkish" to "tr",
        "🇺🇦 Ukrainian" to "uk"
    )

    var expanded by remember { mutableStateOf(false) }
    val selectedLabel by remember(selectedValue) {
        mutableStateOf(
            languages.entries.find { it.value == selectedValue }?.key ?: "Select Language"
        )
    }

    Column(modifier = Modifier.padding(8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onBackground)

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                trailingIcon = {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(Icons.Default.ArrowDropDown, "Dropdown")
                    }
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                languages.forEach { (label, code) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onValueSelected(code)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

fun translate(
    text: String,
    sourceLang: String,
    targetLang: String,
    coroutineScope: CoroutineScope,
    context: Context,
    apiKey: String,
    onResult: (String) -> Unit
) {
    coroutineScope.launch(Dispatchers.IO) {
        try {
            val jsonRequest = """
                {
                    "model": "gpt-4-turbo",
                    "messages": [
                        {"role": "system", "content": "You are a helpful AI that translates text while preserving natural speech. When translating, always convert numbers into words in the target language."},
                        {"role": "user", "content": "Translate the following text from $sourceLang to $targetLang: \"$text\""}
                    ],
                    "temperature": 0.5
                }
            """.trimIndent()

            val requestBody = jsonRequest.toRequestBody("application/json".toMediaTypeOrNull())
            val response = RetrofitInstance.openAITranslateApi.translate("Bearer $apiKey", requestBody)

            if (response.isSuccessful) {
                val translatedText = extractTranslation(response.body()?.string()) ?: "No translation available"
                withContext(Dispatchers.Main) { onResult(translatedText) }
            } else {
                withContext(Dispatchers.Main) { onResult("Translation failed: ${response.code()}") }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult("Error: ${e.message ?: "Unknown error"}") }
        }
    }
}

fun extractTranslation(responseBody: String?): String? {
    return try {
        responseBody?.let {
            val jsonObject = JSONObject(it)
            val choices = jsonObject.getJSONArray("choices")
            if (choices.length() > 0) {
                choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            } else null
        }
    } catch (e: Exception) {
        "Parsing error: ${e.message}"
    }
}

fun playSpeech(text: String, context: Context, elevenLabsKey: String, voiceId: String) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val adjustedText = text.replace("\\s+".toRegex(), " ").trim()
            val jsonRequest = """
                {
                    "text": "$adjustedText",
                    "voice_id": "$voiceId",
                    "model_id": "eleven_multilingual_v2",
                    "voice_settings": {
                        "stability": 0.5,
                        "similarity_boost": 0.8
                    }
                }
            """.trimIndent()
            Log.d("TTS", "Sending request: $jsonRequest")

            val requestBody = jsonRequest.toRequestBody("application/json".toMediaTypeOrNull())
            val response = retryWithBackoff(
                maxRetries = 3,
                initialDelay = 1000L
            ) {
                RetrofitInstance.ttsApi.textToSpeech(elevenLabsKey, requestBody)
            }

            if (response?.isSuccessful == true) {
                val audioFile = File(context.cacheDir, "tts_audio.mp3")
                response.body()?.byteStream()?.use { input ->
                    audioFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    (context as MainActivity).getMediaPlayer().apply {
                        reset()
                        setDataSource(audioFile.absolutePath)
                        prepare()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            playbackParams = playbackParams.setSpeed(0.9f)
                        }
                        start()
                        Log.d("TTS", "MediaPlayer started at speed 0.9")
                    }
                }
                Log.d("TTS", "Speech played successfully")
            } else {
                Log.e("TTS", "API call failed: code=${response?.code()}, message=${response?.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("TTS", "Error playing speech: ${e.message}", e)
        }
    }
}

suspend fun retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Long = 1000L,
    block: suspend () -> Response<ResponseBody>
): Response<ResponseBody>? {
    var attempt = 0
    var delayTime = initialDelay
    while (attempt < maxRetries) {
        val response = block()
        if (response.isSuccessful) return response
        Log.w("TTS", "Retrying TTS request... Attempt: ${attempt + 1}, Code: ${response.code()}")
        attempt++
        if (attempt < maxRetries) {
            delay(delayTime)
            delayTime *= 2 // Exponential backoff
        }
    }
    return null
}