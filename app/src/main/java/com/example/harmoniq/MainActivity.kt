package com.example.harmoniq

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
    private var isPlayingAudio by mutableStateOf(false) // State for audio playback

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
                        voiceId = userVoiceId,
                        isPlayingAudio = isPlayingAudio, // Pass the state
                        onPlayingAudioChange = { newValue -> // Pass the callback
                            isPlayingAudio = newValue
                        }
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
    voiceId: String,
    isPlayingAudio: Boolean, // Receive the state
    onPlayingAudioChange: (Boolean) -> Unit // Callback to update the state
) {
    val coroutineScope = rememberCoroutineScope()
    var text by remember { mutableStateOf("Tap & Speak") }
    var isListening by remember { mutableStateOf(false) }
    var inputLanguage by remember { mutableStateOf("en") }
    var outputLanguage by remember { mutableStateOf("es") }
    var lastTranslatedText by remember { mutableStateOf<String?>(null) } // Store the last translated text

    // Use VibratorManager for API 31+ or Vibrator for older devices
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // Animation for loading indicator
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                text = "Listening..."
                // Haptic for start of listening (30ms)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30)
                }
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                text = "Error occurred: $error. Tap to try again."
                isListening = false
                Log.e("SPEECH", "Recognition error: $error")
                // Haptic for error (three 50ms pulses)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(150)
                }
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
                        lastTranslatedText = translatedText // Store the translated text
                        playSpeech(translatedText, context, elevenLabsApiKey, voiceId, onPlayingAudioChange)
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

            // Modified FloatingActionButton (Swap Languages) to adjust shadow
            FloatingActionButton(
                onClick = {
                    inputLanguage = outputLanguage.also { outputLanguage = inputLanguage }
                    // Haptic feedback for language swap (100ms)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(100)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(56.dp)
                    .padding(10.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = Color.Black.copy(alpha = 0.2f), // Soften the shadow
                        ambientColor = Color.Black.copy(alpha = 0.1f) // Soften the ambient shadow
                    )
            ) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = "Swap input and output languages",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Button(
                onClick = {
                    if (isListening) {
                        speechRecognizer.stopListening()
                        isListening = false
                        text = "Tap & Speak"
                        // Haptic feedback for stopping (50ms)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(50)
                        }
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
                        // Haptic feedback for starting (50ms)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(50)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp)), // Added shadow and rounded corners
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Tap to speak or stop listening",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isListening) "Stop Listening" else "Tap & Speak",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 18.sp,
                    fontFamily = curvanaFont
                )
            }

            // Modified "Repeat Audio" Button to improve disabled state appearance
            Button(
                onClick = {
                    lastTranslatedText?.let { translatedText ->
                        playSpeech(translatedText, context, elevenLabsApiKey, voiceId, onPlayingAudioChange)
                        // Haptic feedback for repeat (50ms)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(50)
                        }
                    } ?: run {
                        Log.w("REPEAT", "No translation available to repeat")
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (lastTranslatedText != null) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContentColor = Color.White.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(
                        elevation = if (lastTranslatedText != null) 4.dp else 0.dp, // No shadow when disabled
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                enabled = lastTranslatedText != null // Disable if no translation exists
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Repeat the last translated audio",
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (lastTranslatedText != null) 1f else 0.6f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Repeat Audio",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (lastTranslatedText != null) 1f else 0.6f),
                    fontSize = 18.sp,
                    fontFamily = curvanaFont
                )
            }

            // Loading Indicator for Audio Playback
            if (isPlayingAudio) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(50.dp)
                        .padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
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
        "🇺🇸 English" to "en",
        "🇪🇸 Spanish" to "es",
        "🇨🇳 Chinese" to "zh",
        "🇫🇷 French" to "fr",
        "🇮🇳 Hindi" to "hi",
        "🇸🇦 Arabic" to "ar",
        "🇵🇹 Portuguese" to "pt",
        "🇷🇺 Russian" to "ru",
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

fun playSpeech(text: String, context: Context, elevenLabsKey: String, voiceId: String, onPlayingAudioChange: (Boolean) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val adjustedText = text.replace("\\s+".toRegex(), " ").trim()
            val jsonRequest = """
                {
                    "text": "$adjustedText",
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
                RetrofitInstance.ttsApi.textToSpeech(voiceId, elevenLabsKey, requestBody)
            }

            if (response?.isSuccessful == true) {
                val audioFile = File(context.cacheDir, "tts_audio_${text.hashCode()}.mp3")
                response.body()?.byteStream()?.use { input ->
                    audioFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    val mainActivity = context as MainActivity
                    mainActivity.getMediaPlayer().apply {
                        reset()
                        setDataSource(audioFile.absolutePath)
                        prepare()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            playbackParams = playbackParams.setSpeed(0.9f)
                        }
                        start()
                        Log.d("TTS", "MediaPlayer started at speed 0.9")
                    }
                    onPlayingAudioChange(true) // Start loading indicator
                    // Stop loading indicator when playback ends
                    mainActivity.getMediaPlayer().setOnCompletionListener {
                        onPlayingAudioChange(false)
                    }
                }
                Log.d("TTS", "Speech played successfully")
            } else {
                Log.e("TTS", "API call failed: code=${response?.code()}, message=${response?.errorBody()?.string()}")
                withContext(Dispatchers.Main) {
                    onPlayingAudioChange(false) // Ensure loading stops on failure
                }
            }
        } catch (e: Exception) {
            Log.e("TTS", "Error playing speech: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onPlayingAudioChange(false) // Ensure loading stops on error
            }
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