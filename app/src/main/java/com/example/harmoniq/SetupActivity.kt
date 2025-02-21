package com.example.harmoniq

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
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

    val coroutineScope = rememberCoroutineScope() // ✅ Use coroutine scope for saving API keys

    // ✅ Fetch saved API keys when the screen loads
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

                    // ✅ Ensure DataStore has saved before navigating
                    delay(500) // Small delay to ensure data is written

                    val intent = Intent(context, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    (context as ComponentActivity).finish() // Close SetupActivity
                }
            }
        ) {
            Text("Save & Continue")
        }
    }
}
