package com.example.harmoniq

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay

class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { // Added Bundle? parameter and ensured @Override
        super.onCreate(savedInstanceState)

        setContent {
            SetupScreen(this)
        }
    }
}

@Composable
fun SetupScreen(context: Context) {
    var openAiKey by remember { mutableStateOf("") }
    var elevenLabsKey by remember { mutableStateOf("") }
    var selectedVoice by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    // Use VibratorManager for API 31+ or Vibrator for older devices
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            openAiKey = DataStoreModule.readApiKey(context.applicationContext, "openai_api_key").first() ?: ""
            elevenLabsKey = DataStoreModule.readApiKey(context.applicationContext, "eleven_labs_api_key").first() ?: ""
            selectedVoice = DataStoreModule.readApiKey(context.applicationContext, "selected_voice").first() ?: ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Setup API Keys", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = openAiKey,
            onValueChange = { openAiKey = it },
            label = { Text("OpenAI API Key") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = elevenLabsKey,
            onValueChange = { elevenLabsKey = it },
            label = { Text("Eleven Labs API Key") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = selectedVoice,
            onValueChange = { selectedVoice = it },
            label = { Text("Eleven Labs Voice ID") }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    DataStoreModule.saveApiKey(context.applicationContext, "openai_api_key", openAiKey)
                    DataStoreModule.saveApiKey(context.applicationContext, "eleven_labs_api_key", elevenLabsKey)
                    DataStoreModule.saveApiKey(context.applicationContext, "selected_voice", selectedVoice)

                    // Haptic feedback for saving (80ms)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(80)
                    }

                    delay(500) // Ensure data is written

                    val intent = Intent(context, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    (context as ComponentActivity).finish()
                }
            }
        ) {
            Text("Save & Continue")
        }
    }
}