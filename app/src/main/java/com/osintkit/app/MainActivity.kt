package com.osintkit.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.osintkit.app.ui.OsintApp
import com.osintkit.app.ui.OsintTheme

class MainActivity : ComponentActivity() {

    /** Paylaşım ile gelen ve henüz işlenmemiş intent. */
    private val incoming = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) incoming.value = intent
        setContent {
            OsintTheme {
                OsintApp(incoming = incoming.value, onIntentHandled = { incoming.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming.value = intent
    }
}
