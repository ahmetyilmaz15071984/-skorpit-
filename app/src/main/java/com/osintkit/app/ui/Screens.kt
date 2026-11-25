@file:OptIn(ExperimentalMaterial3Api::class)

package com.osintkit.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.osintkit.app.core.Found
import com.osintkit.app.core.IpUtil
import com.osintkit.app.core.NetworkLookup
import com.osintkit.app.core.SiteResult
import com.osintkit.app.core.UsernameScanner
import com.osintkit.app.core.toReport

@Composable
private fun ScreenList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun HintCard(lines: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val ETHICS =
    "Yalnızca herkese açık kaynakları sorgular. Kişisel verileri KVKK ve ilgili yasalara uygun, meşru amaçlarla kullanın."

// ==================================================================== Kullanıcı adı

@Composable
fun UsernameScreen(app: AppState, onLink: (String) -> Unit) {
    val s = app.username
    val total = UsernameScanner.sites.size
    val results = s.results.toList()
    val shown = results
        .filter { s.filter.accepts(it.status) }
        .sortedWith(compareBy<SiteResult>({ it.status.ordinal }, { it.site.name.lowercase() }))

    ScreenList {
        item {
            ScreenHeader("Kullanıcı adı taraması", "Bir kullanıcı adının $total sitede kayıtlı olup olmadığını kontrol eder.")
        }
        item {
            QueryBar(
                value = s.query,
                onValueChange = { s.query = it.replace(" ", "") },
                label = "Kullanıcı adı",
                placeholder = "ör. ahmetyilmaz",
                keyboardType = KeyboardType.Uri,
                running = s.running,
                onRun = app::runUsername,
                onCancel = app::cancelUsername,
            )
        }
        if (s.running || results.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${results.size} / $total site kontrol edildi · \"${s.lastQuery}\"",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LinearProgressIndicator(
                        progress = { results.size / total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UsernameFilter.entries.forEach { f ->
                            val n = results.count { f.accepts(it.status) }
                            FilterChip(
                                selected = s.filter == f,
                                onClick = { s.filter = f },
                                label = { Text("${f.label} ($n)") },
                            )
                        }
                    }
                    if (results.isNotEmpty()) ShareRow { usernameReport(s.lastQuery, results) }
                }
            }
        }
        if (results.isNotEmpty() && shown.isEmpty()) {
            item {
                Text(
                    if (s.running) "Bu filtrede henüz sonuç yok…" else "Bu filtrede sonuç yok.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(shown, key = { it.site.name }) { r -> SiteResultCard(r, onLink) }
        if (results.isEmpty() && !s.running) {
            item {
                HintCard(
                    listOf(
                        "Sonuçlar \"VAR\", \"YOK\", \"?\" (site engelledi / belirsiz) ve \"ELLE\" (Instagram, X gibi giriş isteyen siteler) olarak işaretlenir.",
                        "\"VAR\" yalnızca o adla bir hesap olduğunu gösterir; aynı kişiye ait olduğunu kanıtlamaz.",
                        ETHICS,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SiteResultCard(r: SiteResult, onLink: (String) -> Unit) {
    val color = statusColor(r.status)
    Card(onClick = { onLink(r.url) }, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = color.copy(alpha = 0.15f), contentColor = color, shape = RoundedCornerShape(6.dp)) {
                Text(
                    statusLabel(r.status),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).widthIn(min = 34.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.site.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${r.site.category} · ${r.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("↗", color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun usernameReport(q: String, results: List<SiteResult>): String = buildString {
    appendLine("OSINT Kit — Kullanıcı adı taraması: $q")
    fun group(title: String, status: Found, withDetail: Boolean) {
        val list = results.filter { it.status == status }.sortedBy { it.site.name.lowercase() }
        if (list.isEmpty()) return
        appendLine()
        appendLine("## $title (${list.size})")
        list.forEach { appendLine("- ${it.site.name}: ${it.url}" + if (withDetail) "  (${it.detail})" else "") }
    }
    group("Bulunan", Found.FOUND, false)
    group("Belirsiz", Found.UNKNOWN, true)
    group("Elle kontrol edilecek", Found.MANUAL, false)
}

// ==================================================================== Ağ

@Composable
fun NetworkScreen(app: AppState, onLink: (String) -> Unit) {
    val s = app.network
    val sub = app.subdomains
    ScreenList {
        item { ScreenHeader("Alan adı / IP", "DNS kayıtları, WHOIS/RDAP, IP konumu ve ağ sahibi, ters DNS.") }
        item {
            QueryBar(
                value = s.query,
                onValueChange = { s.query = it },
                label = "Alan adı, URL veya IP",
                placeholder = "ör. ornek.com.tr · 8.8.8.8",
                keyboardType = KeyboardType.Uri,
                running = s.running,
                onRun = app::runNetwork,
                onCancel = { app.cancel(s) },
            )
        }
        if (s.running) item { ProgressLine(true) }
        if (s.sections.isNotEmpty()) {
            item { ShareRow { (s.sections + sub.sections).toReport("OSINT Kit — Ağ analizi: ${s.lastQuery}") } }
        }
        items(s.sections) { SectionCard(it, onLink) }

        val isDomain = s.lastQuery.isNotBlank() && !IpUtil.isIp(NetworkLookup.normalizeTarget(s.lastQuery))
        if (!s.running && s.sections.isNotEmpty() && isDomain) {
            if (sub.running) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("crt.sh sorgulanıyor (30 saniyeyi bulabilir)…", style = MaterialTheme.typography.bodySmall)
                        ProgressLine(true)
                    }
                }
            } else if (sub.sections.isEmpty()) {
                item {
                    OutlinedButton(onClick = app::runSubdomains, modifier = Modifier.fillMaxWidth()) {
                        Text("Alt alan adlarını bul (sertifika kayıtları)")
                    }
                }
            }
            items(sub.sections) { SectionCard(it, onLink) }
        }
        if (s.sections.isEmpty() && !s.running) {
            item {
                HintCard(
                    listOf(
                        "Alan adı: DNS (A, MX, NS, TXT…), SPF/DMARC, kayıt firması, oluşturulma ve bitiş tarihi, ham WHOIS.",
                        "IP: yaklaşık şehir, internet sağlayıcısı/ASN, IP bloğunun sahibi, ters DNS.",
                        "Sonuçlardaki → işaretli satırlar uygulama içinde yeni bir sorgu başlatır.",
                        ETHICS,
                    ),
                )
            }
        }
    }
}

// ==================================================================== Fotoğraf

@Composable
fun PhotoScreen(app: AppState, onLink: (String) -> Unit) {
    val s = app.photo
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) app.runPhoto(uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // İzin verilmese de devam et; yalnızca GPS gizlenmiş olabilir
        picker.launch(arrayOf("image/*"))
    }
    val pick = {
        val needsPermission = Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) permission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) else picker.launch(arrayOf("image/*"))
    }

    ScreenList {
        item { ScreenHeader("Fotoğraf metadata", "EXIF etiketlerini okur: konum (GPS), cihaz, tarih, yazılım, seri numarası.") }
        item {
            Button(onClick = pick, enabled = !s.running, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Fotoğraf seç")
            }
        }
        if (s.running) item { ProgressLine(true) }
        if (s.sections.isNotEmpty()) item { ShareRow { s.sections.toReport("OSINT Kit — Fotoğraf metadata") } }
        items(s.sections) { SectionCard(it, onLink) }
        if (s.sections.isEmpty() && !s.running) {
            item {
                HintCard(
                    listOf(
                        "Galeride bir fotoğrafı \"Paylaş → OSINT Kit\" ile de gönderebilirsiniz.",
                        "En iyi sonuç için orijinal dosyayı seçin (ör. Dosyalar › İndirilenler veya DCIM). Mesajlaşma uygulamaları metadatayı siler.",
                        "Fotoğraf cihazdan dışarı gönderilmez; analiz tamamen telefonda yapılır.",
                    ),
                )
            }
        }
    }
}

// ==================================================================== E-posta

@Composable
fun EmailScreen(app: AppState, onLink: (String) -> Unit) {
    val s = app.email
    ScreenList {
        item { ScreenHeader("E-posta analizi", "Biçim, alan adı ve posta sunucusu, sağlayıcı türü, Gravatar profili.") }
        item {
            QueryBar(
                value = s.query,
                onValueChange = { s.query = it.trim() },
                label = "E-posta adresi",
                placeholder = "ör. ad.soyad@ornek.com",
                keyboardType = KeyboardType.Email,
                running = s.running,
                onRun = app::runEmail,
                onCancel = { app.cancel(s) },
            )
        }
        if (s.running) item { ProgressLine(true) }
        if (s.sections.isNotEmpty()) item { ShareRow { s.sections.toReport("OSINT Kit — E-posta: ${s.lastQuery}") } }
        items(s.sections) { SectionCard(it, onLink) }
        if (s.sections.isEmpty() && !s.running) {
            item {
                HintCard(
                    listOf(
                        "Tek kullanımlık (geçici) ve gizleyici adresleri tanır.",
                        "Alan adının e-posta alıp alamadığını ve hangi sağlayıcıda barındığını gösterir.",
                        "Sızıntı kontrolü için Have I Been Pwned bağlantısı verilir (resmî API ücretli).",
                        ETHICS,
                    ),
                )
            }
        }
    }
}

// ==================================================================== Telefon

@Composable
fun PhoneScreen(app: AppState, onLink: (String) -> Unit) {
    val s = app.phone
    ScreenList {
        item { ScreenHeader("Telefon numarası", "Ülke, hat türü, biçimler ve (Türkiye için) ilk tahsis edilen operatör.") }
        item {
            QueryBar(
                value = s.query,
                onValueChange = { s.query = it },
                label = "Telefon numarası",
                placeholder = "ör. 0532 123 45 67 · +44 20…",
                keyboardType = KeyboardType.Phone,
                running = s.running,
                onRun = app::runPhone,
                onCancel = { app.cancel(s) },
            )
        }
        if (s.running) item { ProgressLine(true) }
        if (s.sections.isNotEmpty()) item { ShareRow { s.sections.toReport("OSINT Kit — Telefon: ${s.lastQuery}") } }
        items(s.sections) { SectionCard(it, onLink) }
        if (s.sections.isEmpty() && !s.running) {
            item {
                HintCard(
                    listOf(
                        "Ülke kodu yazılmazsa Türkiye (+90) varsayılır.",
                        "Çözümleme internet kullanmadan, telefonda yapılır.",
                        ETHICS,
                    ),
                )
            }
        }
    }
}
