@file:OptIn(ExperimentalFoundationApi::class)

package com.osintkit.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.osintkit.app.MainActivity
import com.osintkit.app.core.InfoRow
import com.osintkit.app.core.InternalLinks
import com.osintkit.app.core.Section

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun QueryBar(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
    running: Boolean,
    onRun: () -> Unit,
    onCancel: () -> Unit,
) {
    val focus = LocalFocusManager.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                if (value.isNotBlank()) onRun()
            }),
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Clear, contentDescription = "Temizle") }
                }
            },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (running) {
            FilledTonalButton(onClick = onCancel) { Text("Durdur") }
        } else {
            Button(
                onClick = {
                    focus.clearFocus()
                    onRun()
                },
                enabled = value.isNotBlank(),
            ) { Text("Ara") }
        }
    }
}

@Composable
fun ProgressLine(running: Boolean) {
    if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}

@Composable
fun ShareRow(report: () -> String) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Dokun: aç · Uzun bas: kopyala",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { shareText(ctx, report()) }) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Raporu paylaş")
        }
    }
}

@Composable
fun SectionCard(section: Section, onLink: (String) -> Unit) {
    val colors = if (section.isError) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        CardDefaults.cardColors()
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            section.rows.forEachIndexed { i, row ->
                if (i > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                }
                InfoRowView(row, onLink)
            }
        }
    }
}

@Composable
fun InfoRowView(row: InfoRow, onLink: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val link = row.link
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (link != null) onLink(link) },
                onLongClick = {
                    clipboard.setText(AnnotatedString(row.value))
                    Toast.makeText(ctx, "Kopyalandı", Toast.LENGTH_SHORT).show()
                },
            )
            .padding(vertical = 2.dp),
    ) {
        if (row.label.isNotEmpty()) {
            Text(row.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val mono = row.label.isEmpty() && row.value.contains('\n')
        val style = if (mono) {
            MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        } else {
            MaterialTheme.typography.bodyMedium
        }
        if (link != null) {
            val arrow = if (InternalLinks.isInternal(link)) "  →" else "  ↗"
            Text(row.value + arrow, style = style, color = MaterialTheme.colorScheme.primary)
        } else {
            Text(row.value, style = style)
        }
    }
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, "Raporu paylaş").apply {
        // Uygulamanın kendisi de metin kabul ettiği için listeden çıkar
        putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
    }
    context.startActivity(chooser)
}
