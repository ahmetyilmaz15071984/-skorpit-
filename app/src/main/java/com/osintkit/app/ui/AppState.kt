package com.osintkit.app.ui

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.osintkit.app.android.ExifReader
import com.osintkit.app.android.PhoneAnalyzer
import com.osintkit.app.core.EmailAnalyzer
import com.osintkit.app.core.Found
import com.osintkit.app.core.InternalLinks
import com.osintkit.app.core.IpUtil
import com.osintkit.app.core.NetworkLookup
import com.osintkit.app.core.Section
import com.osintkit.app.core.SiteResult
import com.osintkit.app.core.UsernameScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

enum class Tab(val label: String, val icon: ImageVector) {
    USERNAME("Kullanıcı", Icons.Default.Person),
    NETWORK("Ağ", Icons.Default.Search),
    PHOTO("Foto", Icons.Default.LocationOn),
    EMAIL("E-posta", Icons.Default.Email),
    PHONE("Telefon", Icons.Default.Phone),
}

enum class UsernameFilter(val label: String) {
    FOUND("Bulunan"),
    UNKNOWN("Belirsiz"),
    MANUAL("Elle bak"),
    ALL("Tümü");

    fun accepts(s: Found) = when (this) {
        FOUND -> s == Found.FOUND
        UNKNOWN -> s == Found.UNKNOWN
        MANUAL -> s == Found.MANUAL
        ALL -> true
    }
}

/** Ağ, foto, e-posta, telefon ekranları için ortak durum. */
class LookupState {
    var query by mutableStateOf("")
    var lastQuery by mutableStateOf("")
    var sections by mutableStateOf<List<Section>>(emptyList())
    var running by mutableStateOf(false)
    internal var job: Job? = null
    internal var generation = 0

    fun clear() {
        job?.cancel()
        generation++
        sections = emptyList()
        running = false
    }
}

class UsernameState {
    var query by mutableStateOf("")
    var lastQuery by mutableStateOf("")
    val results = mutableStateListOf<SiteResult>()
    var running by mutableStateOf(false)
    var filter by mutableStateOf(UsernameFilter.FOUND)
    internal var job: Job? = null
    internal var generation = 0
}

/**
 * Uygulama durumu. Sekme değiştirince sonuçlar kaybolmasın diye ekranların dışında tutulur.
 * Ağ işlemleri IO iş parçacığında çalışır; sonuçlar ana iş parçacığına aktarılır.
 */
class AppState(private val scope: CoroutineScope, private val appContext: Context) {
    var tab by mutableStateOf(Tab.USERNAME)
    val username = UsernameState()
    val network = LookupState()
    val subdomains = LookupState()
    val photo = LookupState()
    val email = LookupState()
    val phone = LookupState()

    private val main = Handler(Looper.getMainLooper())

    // ------------------------------------------------------------ kullanıcı adı

    fun runUsername() {
        val s = username
        val q = s.query.trim().removePrefix("@")
        if (!UsernameScanner.isValidUsername(q)) return
        s.query = q
        s.job?.cancel()
        val gen = ++s.generation
        s.results.clear()
        s.lastQuery = q
        s.running = true
        s.filter = UsernameFilter.FOUND
        s.job = scope.launch {
            try {
                runInterruptible(Dispatchers.IO) {
                    UsernameScanner.scan(q) { r -> main.post { if (s.generation == gen) s.results.add(r) } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // tek tek site hataları zaten sonuçlarda; burada yalnızca beklenmeyen durum
            } finally {
                main.post { if (s.generation == gen) s.running = false }
            }
        }
    }

    fun cancelUsername() {
        username.job?.cancel()
        username.running = false
    }

    // ------------------------------------------------------------ diğer modüller

    fun runNetwork() {
        if (network.query.isBlank()) return
        subdomains.clear()
        val q = network.query
        runLookup(network) { emit -> NetworkLookup.lookup(q, emit) }
    }

    fun runSubdomains() {
        val q = network.lastQuery.ifBlank { network.query }
        if (q.isBlank()) return
        subdomains.query = q
        runLookup(subdomains) { emit -> emit(NetworkLookup.subdomains(q)) }
    }

    fun runEmail() {
        if (email.query.isBlank()) return
        val q = email.query
        runLookup(email) { emit -> EmailAnalyzer.analyze(q, emit) }
    }

    fun runPhone() {
        if (phone.query.isBlank()) return
        val q = phone.query
        runLookup(phone) { emit -> PhoneAnalyzer.analyze(q).forEach(emit) }
    }

    fun runPhoto(uri: Uri) {
        photo.query = uri.toString()
        runLookup(photo) { emit -> ExifReader.analyze(appContext, uri).forEach(emit) }
    }

    fun cancel(state: LookupState) {
        state.job?.cancel()
        state.generation++
        state.running = false
    }

    private fun runLookup(state: LookupState, block: (emit: (Section) -> Unit) -> Unit) {
        state.job?.cancel()
        val gen = ++state.generation
        state.sections = emptyList()
        state.lastQuery = state.query.trim()
        state.running = true
        state.job = scope.launch {
            try {
                runInterruptible(Dispatchers.IO) {
                    block { section ->
                        main.post { if (state.generation == gen) state.sections = state.sections + section }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (state.generation == gen) state.sections = state.sections + Section.fromException("Hata", e)
            } finally {
                main.post { if (state.generation == gen) state.running = false }
            }
        }
    }

    // ------------------------------------------------------------ yönlendirme

    /** Sonuçlardaki osint:// bağlantıları ilgili sekmede açar ve sorguyu başlatır. */
    fun openInternal(link: String) {
        val t = InternalLinks.parse(link) ?: return
        when (t.module) {
            InternalLinks.USERNAME -> { tab = Tab.USERNAME; username.query = t.query; runUsername() }
            InternalLinks.NETWORK -> { tab = Tab.NETWORK; network.query = t.query; runNetwork() }
            InternalLinks.EMAIL -> { tab = Tab.EMAIL; email.query = t.query; runEmail() }
            InternalLinks.PHONE -> { tab = Tab.PHONE; phone.query = t.query; runPhone() }
        }
    }

    /** Başka uygulamadan paylaşılan metni türüne göre doğru modüle gönderir. */
    fun handleSharedText(text: String) {
        val url = Regex("""https?://\S+""").find(text)?.value
        val t = (url ?: text).trim()
        val digits = t.count { it.isDigit() }
        when {
            url != null -> openInternal(InternalLinks.network(t))
            EmailAnalyzer.isValid(t.lowercase()) -> openInternal(InternalLinks.email(t))
            PHONE_RE.matches(t) && digits in 7..15 -> openInternal(InternalLinks.phone(t))
            IpUtil.isIp(t) || (t.contains('.') && NetworkLookup.isValidDomain(NetworkLookup.normalizeTarget(t))) ->
                openInternal(InternalLinks.network(t))
            UsernameScanner.isValidUsername(t.removePrefix("@")) -> openInternal(InternalLinks.username(t.removePrefix("@")))
            else -> { tab = Tab.NETWORK; network.query = t }
        }
    }

    private companion object {
        val PHONE_RE = Regex("""^\+?[0-9 ()\-.]{7,22}$""")
    }
}
