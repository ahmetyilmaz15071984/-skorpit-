package com.osintkit.app.core

import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.Executors

enum class Check {
    /** 2xx = var, missingCodes (varsayılan 404/410) = yok */
    STATUS,
    /** Yanıtta [Site.text] geçiyorsa = yok */
    ERROR_TEXT,
    /** Yanıtta [Site.text] geçiyorsa = var */
    PRESENCE_TEXT,
    /** Otomatik kontrol güvenilir değil; yalnızca bağlantı verilir */
    MANUAL,
}

enum class Found { FOUND, NOT_FOUND, UNKNOWN, MANUAL, SKIPPED }

data class Site(
    val name: String,
    val category: String,
    /** Profil adresi; {} kullanıcı adıyla değiştirilir. */
    val profileUrl: String,
    /** Kontrol için farklı bir adres (genelde API). Boşsa profileUrl kullanılır. */
    val checkUrl: String? = null,
    val check: Check = Check.STATUS,
    val text: String? = null,
    val missingCodes: Set<Int> = setOf(404, 410),
    /** Kullanıcı adı alt alan adında kullanılıyor (ornek.tumblr.com): yalnızca harf/rakam/tire. */
    val subdomain: Boolean = false,
    val lowercase: Boolean = false,
)

data class SiteResult(
    val site: Site,
    val status: Found,
    val url: String,
    val detail: String,
)

object UsernameScanner {

    private val SUBDOMAIN_RE = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")

    private const val DEV = "Yazılım"
    private const val SOCIAL = "Sosyal"
    private const val MEDIA = "Medya ve sanat"
    private const val GAME = "Oyun"
    private const val OTHER = "Diğer"

    val sites: List<Site> = listOf(
        // --- Yazılım ---
        Site("GitHub", DEV, "https://github.com/{}"),
        Site("GitLab", DEV, "https://gitlab.com/{}", "https://gitlab.com/api/v4/users?username={}", Check.ERROR_TEXT, "[]"),
        Site("Bitbucket", DEV, "https://bitbucket.org/{}/"),
        Site("Docker Hub", DEV, "https://hub.docker.com/u/{}", "https://hub.docker.com/v2/users/{}/"),
        Site("PyPI", DEV, "https://pypi.org/user/{}/"),
        Site("npm", DEV, "https://www.npmjs.com/~{}"),
        Site("DEV Community", DEV, "https://dev.to/{}"),
        Site("Hacker News", DEV, "https://news.ycombinator.com/user?id={}", check = Check.ERROR_TEXT, text = "No such user."),
        Site("Keybase", DEV, "https://keybase.io/{}"),
        Site("Replit", DEV, "https://replit.com/@{}"),
        Site("Hugging Face", DEV, "https://huggingface.co/{}"),
        Site("Kaggle", DEV, "https://www.kaggle.com/{}"),
        Site("Codeforces", DEV, "https://codeforces.com/profile/{}", "https://codeforces.com/api/user.info?handles={}", missingCodes = setOf(400, 404)),
        Site("Trello", DEV, "https://trello.com/{}", "https://trello.com/1/Members/{}"),

        // --- Sosyal ---
        Site("Reddit", SOCIAL, "https://www.reddit.com/user/{}", "https://www.reddit.com/user/{}/about.json"),
        Site("Telegram", SOCIAL, "https://t.me/{}", check = Check.ERROR_TEXT, text = "<title>Telegram Messenger</title>"),
        Site("Mastodon (mastodon.social)", SOCIAL, "https://mastodon.social/@{}", "https://mastodon.social/api/v1/accounts/lookup?acct={}"),
        Site(
            "Bluesky", SOCIAL, "https://bsky.app/profile/{}.bsky.social",
            "https://public.api.bsky.app/xrpc/app.bsky.actor.getProfile?actor={}.bsky.social",
            missingCodes = setOf(400, 404), subdomain = true, lowercase = true,
        ),
        Site("Medium", SOCIAL, "https://medium.com/@{}"),
        Site("Tumblr", SOCIAL, "https://{}.tumblr.com", subdomain = true),
        Site("Blogger", SOCIAL, "https://{}.blogspot.com", subdomain = true),
        Site("Linktree", SOCIAL, "https://linktr.ee/{}"),
        Site("About.me", SOCIAL, "https://about.me/{}"),
        Site("Disqus", SOCIAL, "https://disqus.com/by/{}/"),
        Site("Ekşi Sözlük", SOCIAL, "https://eksisozluk.com/biri/{}"),
        Site("Patreon", SOCIAL, "https://www.patreon.com/{}"),
        Site("Gravatar", SOCIAL, "https://gravatar.com/{}", "https://en.gravatar.com/{}.json"),
        Site("Instagram", SOCIAL, "https://www.instagram.com/{}/", check = Check.MANUAL),
        Site("X (Twitter)", SOCIAL, "https://x.com/{}", check = Check.MANUAL),
        Site("Facebook", SOCIAL, "https://www.facebook.com/{}", check = Check.MANUAL),
        Site("TikTok", SOCIAL, "https://www.tiktok.com/@{}", check = Check.MANUAL),
        Site("Threads", SOCIAL, "https://www.threads.net/@{}", check = Check.MANUAL),
        Site("LinkedIn", SOCIAL, "https://www.linkedin.com/in/{}", check = Check.MANUAL),
        Site("Snapchat", SOCIAL, "https://www.snapchat.com/add/{}", check = Check.MANUAL),
        Site("Pinterest", SOCIAL, "https://www.pinterest.com/{}/", check = Check.MANUAL),
        Site("VK", SOCIAL, "https://vk.com/{}", check = Check.MANUAL),

        // --- Medya ve sanat ---
        Site("YouTube", MEDIA, "https://www.youtube.com/@{}"),
        Site("SoundCloud", MEDIA, "https://soundcloud.com/{}"),
        Site("Vimeo", MEDIA, "https://vimeo.com/{}"),
        Site("Flickr", MEDIA, "https://www.flickr.com/people/{}"),
        Site("DeviantArt", MEDIA, "https://www.deviantart.com/{}"),
        Site("Behance", MEDIA, "https://www.behance.net/{}"),
        Site("Dribbble", MEDIA, "https://dribbble.com/{}"),
        Site("Last.fm", MEDIA, "https://www.last.fm/user/{}"),
        Site("Letterboxd", MEDIA, "https://letterboxd.com/{}/"),
        Site("Wattpad", MEDIA, "https://www.wattpad.com/user/{}"),
        Site("Spotify", MEDIA, "https://open.spotify.com/user/{}", check = Check.MANUAL),
        Site("Twitch", MEDIA, "https://www.twitch.tv/{}", check = Check.MANUAL),

        // --- Oyun ---
        Site("Steam", GAME, "https://steamcommunity.com/id/{}", check = Check.ERROR_TEXT, text = "The specified profile could not be found."),
        Site("Chess.com", GAME, "https://www.chess.com/member/{}", "https://api.chess.com/pub/player/{}", lowercase = true),
        Site("Lichess", GAME, "https://lichess.org/@/{}", "https://lichess.org/api/user/{}"),
        Site("Minecraft", GAME, "https://namemc.com/profile/{}", "https://api.mojang.com/users/profiles/minecraft/{}", missingCodes = setOf(204, 404)),
        Site("itch.io", GAME, "https://{}.itch.io", subdomain = true),
        Site("Duolingo", GAME, "https://www.duolingo.com/profile/{}", "https://www.duolingo.com/2017-06-30/users?username={}", Check.ERROR_TEXT, "\"users\":[]"),

        // --- Diğer ---
        Site(
            "Wikipedia / Wikimedia", OTHER, "https://meta.wikimedia.org/wiki/Special:CentralAuth/{}",
            "https://meta.wikimedia.org/w/api.php?action=query&meta=globaluserinfo&guiuser={}&format=json",
            Check.ERROR_TEXT, "\"missing\"",
        ),
        Site("Pastebin", OTHER, "https://pastebin.com/u/{}"),
    )

    fun isValidUsername(u: String): Boolean =
        u.isNotBlank() && u.length <= 64 && u.none { it.isWhitespace() || it in "/?#&%\\\"'<>" }

    fun buildUrl(template: String, username: String, site: Site): String {
        val u = if (site.lowercase) username.lowercase() else username
        return template.replace("{}", urlEncode(u))
    }

    fun checkSite(
        site: Site,
        username: String,
        fetch: (String) -> Http.Response = { Http.get(it, timeoutMs = 12_000, maxBytes = 400_000) },
    ): SiteResult {
        val profile = buildUrl(site.profileUrl, username, site)
        if (site.subdomain && !SUBDOMAIN_RE.matches(username)) {
            return SiteResult(site, Found.SKIPPED, profile, "Bu sitede geçersiz biçim (yalnızca harf, rakam, tire)")
        }
        if (site.check == Check.MANUAL) {
            return SiteResult(site, Found.MANUAL, profile, "Otomatik kontrol güvenilir değil — bağlantıyı açıp bakın")
        }
        val url = buildUrl(site.checkUrl ?: site.profileUrl, username, site)
        val response = try {
            fetch(url)
        } catch (e: Exception) {
            return SiteResult(site, Found.UNKNOWN, profile, describeError(e))
        }
        val (status, detail) = classify(site, response)
        return SiteResult(site, status, profile, detail)
    }

    fun classify(site: Site, r: Http.Response): Pair<Found, String> {
        val code = r.code
        if (code in site.missingCodes) return Found.NOT_FOUND to "HTTP $code"
        if (looksLikeWall(r)) return Found.UNKNOWN to "Giriş / doğrulama sayfasına takıldı"
        if (r.redirected && redirectedToHome(r)) return Found.NOT_FOUND to "Ana sayfaya yönlendirildi"

        return when (site.check) {
            Check.STATUS -> when (code) {
                in 200..299 -> Found.FOUND to "HTTP $code"
                403, 429, 503 -> Found.UNKNOWN to "Engellendi / hız sınırı (HTTP $code)"
                else -> Found.UNKNOWN to "HTTP $code"
            }
            Check.ERROR_TEXT -> when {
                site.text != null && r.body.contains(site.text) -> Found.NOT_FOUND to "Site 'kullanıcı yok' diyor"
                code in 200..299 -> Found.FOUND to "HTTP $code"
                code == 403 || code == 429 -> Found.UNKNOWN to "Engellendi / hız sınırı (HTTP $code)"
                else -> Found.UNKNOWN to "HTTP $code"
            }
            Check.PRESENCE_TEXT -> when {
                code !in 200..299 -> Found.UNKNOWN to "HTTP $code"
                site.text != null && r.body.contains(site.text) -> Found.FOUND to "HTTP $code"
                else -> Found.NOT_FOUND to "Profil işareti yok"
            }
            Check.MANUAL -> Found.MANUAL to ""
        }
    }

    private fun looksLikeWall(r: Http.Response): Boolean {
        val u = r.finalUrl.lowercase()
        if (r.redirected && listOf("/login", "/signin", "/sign_in", "/accounts/", "consent.", "/challenge", "captcha", "/auth").any { u.contains(it) }) {
            return true
        }
        val head = r.body.take(4000)
        return head.contains("<title>Just a moment...</title>") || head.contains("challenge-platform") ||
            head.contains("cf-browser-verification")
    }

    private fun redirectedToHome(r: Http.Response): Boolean {
        val from = runCatching { URI(r.requestUrl) }.getOrNull() ?: return false
        val to = runCatching { URI(r.finalUrl) }.getOrNull() ?: return false
        val toPath = to.path ?: ""
        val fromPath = from.path ?: ""
        if (toPath.isNotEmpty() && toPath != "/") return false
        val fh = from.host?.lowercase() ?: return false
        val th = to.host?.lowercase() ?: return false
        // Aynı sitede /kullanici -> / : kullanıcı yok
        if (fh == th) return fromPath.length > 1
        // kullanici.site.com -> www.site.com/ : kullanıcı yok
        // site.com/@kullanici -> kullanici.site.com/ : kullanıcı VAR (Medium gibi)
        val bare = th.removePrefix("www.")
        return th.startsWith("www.") || fh == bare || fh.endsWith(".$bare")
    }

    /**
     * Tüm siteleri paralel tarar. Her sonuç geldikçe [onResult] çağrılır (arka plan iş parçacığında).
     * Çağıran iş parçacığı kesilirse (interrupt) tarama durur.
     */
    fun scan(
        username: String,
        sites: List<Site> = this.sites,
        threads: Int = 12,
        fetch: ((String) -> Http.Response)? = null,
        onResult: (SiteResult) -> Unit,
    ) {
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val tasks = sites.map { s ->
                Callable {
                    val res = if (fetch != null) checkSite(s, username, fetch) else checkSite(s, username)
                    if (!Thread.currentThread().isInterrupted) onResult(res)
                    res
                }
            }
            pool.invokeAll(tasks)
        } finally {
            pool.shutdownNow()
        }
    }
}
