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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.util.Locale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape





// Define the Curvana font
val curvanaFont = FontFamily(Font(R.font.curvana)) // Ensure you have curvana.ttf in res/font/

class MainActivity : ComponentActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setContent {
            val isDarkMode = isSystemInDarkTheme() // ✅ Now it should work
            AppTheme(darkTheme = isDarkMode) {
                AppUI(this, speechRecognizer, coroutineScope)
            }
        }
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }
}

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF00BFA6),  // ✅ Keep Teal
            background = Color(0xFF121212), // Dark background
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.Black,
            onBackground = Color.White
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF00BFA6),  // ✅ Keep Teal
            background = Color(0xFFFFFFFF), // Light background
            surface = Color(0xFFF5F5F5),
            onPrimary = Color.Black,
            onBackground = Color.Black
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}


@Composable
fun AppUI(context: Context, speechRecognizer: SpeechRecognizer, coroutineScope: CoroutineScope) {
    val tealColor = Color(0xFF00BFA6)
    var text by remember { mutableStateOf("Tap & Speak") }
    var isListening by remember { mutableStateOf(false) }

    var inputLanguage by remember { mutableStateOf("en") }
    var outputLanguage by remember { mutableStateOf("es") }  // Defaulting to different values

    val startListening: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (inputLanguage == "auto") Locale.getDefault().toString() else inputLanguage)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { text = "Error occurred. Try again." }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    text = recognizedText
                    translate(recognizedText, inputLanguage, outputLanguage, coroutineScope, context) {
                        text = it
                        playSpeech(it, context) // ✅ Call Eleven Labs TTS after translation
                    }
                }
                isListening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer.startListening(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title remains at the top
        Text(
            text = "Harmoniq",
            fontFamily = curvanaFont,
            fontSize = 56.sp, // Large title
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ✅ New Column to Center the Rest
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f), // Pushes content to center
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            SelectionContainer {
                Text(
                    text = text,
                    color = Color(0xFF00BFA6), // ✅ Translated text remains teal
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }

            LanguageDropdown("Select Input Language", inputLanguage) { inputLanguage = it }
            LanguageDropdown("Select Output Language", outputLanguage) { outputLanguage = it }

            FloatingActionButton(
                onClick = {
                    val temp = inputLanguage
                    inputLanguage = outputLanguage
                    outputLanguage = temp
                },
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
                        startListening()
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
fun LanguageDropdown(label: String, selectedValue: String, onValueSelected: (String) -> Unit) {
    val languages = mapOf(
        "English" to "en", "Spanish" to "es", "French" to "fr", "German" to "de",
        "Korean" to "ko", "Japanese" to "ja", "Thai" to "th", "Chinese (Simplified)" to "zh-CN",
        "Chinese (Traditional)" to "zh-TW", "Russian" to "ru", "Portuguese" to "pt",
        "Italian" to "it", "Dutch" to "nl", "Hindi" to "hi", "Arabic" to "ar",
        "Turkish" to "tr", "Polish" to "pl", "Hebrew" to "he", "Vietnamese" to "vi",
        "Indonesian" to "id", "Swedish" to "sv", "Greek" to "el", "Czech" to "cs",
        "Hungarian" to "hu", "Danish" to "da", "Finnish" to "fi", "Ukrainian" to "uk",
        "Malay" to "ms", "Bengali" to "bn", "Filipino (Tagalog)" to "tl"
    )

    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = languages.entries.find { it.value == selectedValue }?.key ?: "Select Language"

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
                shape = RoundedCornerShape(16.dp), // ✅ Rounded corners
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                trailingIcon = {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown Icon")
                    }
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)) // ✅ Rounded dropdown
            ) {
                languages.entries.forEach { (label, code) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onValueSelected(code)
                            expanded = false
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}




                // ✅ Translate Function (Added Back)
fun translate(
    text: String,
    sourceLang: String,
    targetLang: String,
    coroutineScope: CoroutineScope,
    context: Context,
    onResult: (String) -> Unit
) {
    coroutineScope.launch {
        try {
            val apiKey = BuildConfig.GOOGLE_TRANSLATE_API_KEY

            val response = RetrofitInstance.translateApi.translateText(
                text = text,
                sourceLang = sourceLang,
                targetLanguage = targetLang,
                apiKey = apiKey
            )

            if (response.data.translations.isNotEmpty()) {
                val translatedText = response.data.translations.first().translatedText
                onResult(translatedText)
            } else {
                onResult("Error: No translation received")
            }
        } catch (e: Exception) {
            onResult("Error: ${e.localizedMessage}")
        }
    }
}

// ✅ Eleven Labs TTS
fun playSpeech(text: String, context: Context) {
    val coroutineScope = CoroutineScope(Dispatchers.IO)
    coroutineScope.launch {
        try {
            val apiKey = BuildConfig.ELEVEN_LABS_API_KEY
            Log.d("TTS_DEBUG", "Using API Key: $apiKey") // ✅ Debug API Key

            if (apiKey.isBlank()) {
                Log.e("TTS_ERROR", "API Key is missing!")
                return@launch
            }

            val jsonRequest = """
    {
        "text": "$text",
        "voice_id": "4AZCG1BRUah8dBIuD0Ik",
        "model_id": "eleven_multilingual_v1",
        "voice_settings": {
            "stability": 0.5,
            "similarity_boost": 0.8
        }
    }
""".trimIndent()




            val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), jsonRequest)
            val response: Response<ResponseBody> = RetrofitInstance.ttsApi.textToSpeech(apiKey, requestBody)

            Log.d("TTS_DEBUG", "API Response Code: ${response.code()}") // ✅ Log Response Code
            Log.d("TTS_DEBUG", "API Response Body: ${response.errorBody()?.string()}") // ✅ Log Error Response

            if (!response.isSuccessful) {
                Log.e("TTS_ERROR", "API request failed: ${response.code()} - ${response.errorBody()?.string()}")
                return@launch
            }

            val audioFile = File(context.cacheDir, "tts_audio.mp3")
            response.body()?.byteStream()?.use { input ->
                audioFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                start()
            }

            Log.d("TTS_SUCCESS", "Speech played successfully with Amos voice!")

        } catch (e: Exception) {
            Log.e("TTS_ERROR", "Failed to play speech: ${e.localizedMessage}", e)
        }
    }
}


fun playAudio(filePath: String) {
    try {
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            start()
        }
        Log.d("MEDIA_PLAYER", "Playing audio: $filePath")
    } catch (e: Exception) {
        Log.e("MEDIA_PLAYER_ERROR", "Failed to play audio: ${e.localizedMessage}", e)
    }
}


