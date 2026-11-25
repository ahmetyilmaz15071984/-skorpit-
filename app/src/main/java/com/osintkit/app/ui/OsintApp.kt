package com.osintkit.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.IntentCompat
import com.osintkit.app.core.InternalLinks

@Composable
fun OsintApp(incoming: Intent?, onIntentHandled: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state = remember { AppState(scope, context.applicationContext) }
    val uriHandler = LocalUriHandler.current

    val onLink: (String) -> Unit = { link ->
        if (InternalLinks.isInternal(link)) {
            state.openInternal(link)
        } else {
            try {
                uriHandler.openUri(link)
            } catch (e: Exception) {
                Toast.makeText(context, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Başka uygulamadan "Paylaş" ile gelen fotoğraf veya metin
    LaunchedEffect(incoming) {
        val intent = incoming ?: return@LaunchedEffect
        if (intent.action == Intent.ACTION_SEND) {
            val type = intent.type.orEmpty()
            if (type.startsWith("image/")) {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
                    state.tab = Tab.PHOTO
                    state.runPhoto(it)
                }
            } else {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { state.handleSharedText(it) }
            }
        }
        onIntentHandled()
    }

    BackHandler(enabled = state.tab != Tab.USERNAME) { state.tab = Tab.USERNAME }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = state.tab == t,
                        onClick = { state.tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (state.tab) {
                Tab.USERNAME -> UsernameScreen(state, onLink)
                Tab.NETWORK -> NetworkScreen(state, onLink)
                Tab.PHOTO -> PhotoScreen(state, onLink)
                Tab.EMAIL -> EmailScreen(state, onLink)
                Tab.PHONE -> PhoneScreen(state, onLink)
            }
        }
    }
}
