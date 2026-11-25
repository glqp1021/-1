package com.relayswitch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relayswitch.app.ui.ChatScreen
import com.relayswitch.app.ui.ChatViewModel
import com.relayswitch.app.ui.theme.RelaySwitchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RelaySwitchTheme {
                val vm: ChatViewModel = viewModel()
                ChatScreen(viewModel = vm)
            }
        }
    }
}
