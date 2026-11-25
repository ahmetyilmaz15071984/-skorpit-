package com.osintkit.app.android

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.osintkit.app.core.InfoRow
import com.osintkit.app.core.Section
import com.osintkit.app.core.truncate
import java.util.Locale
import kotlin.math.roundToInt

/** Fotoğraf metadata (EXIF/XMP/GPS) okuyucu. JPEG, PNG, WebP, HEIC ve RAW (DNG vb.) destekler. */
object ExifReader {

    private val HIGHLIGHT = listOf(
        ExifInterface.TAG_MAKE to "Üretici",
        ExifInterface.TAG_MODEL to "Model",
        ExifInterface.TAG_SOFTWARE to "Yazılım",
        ExifInterface.TAG_DATETIME_ORIGINAL to "Çekim tarihi",
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL to "Saat dilimi (çekim)",
        ExifInterface.TAG_DATETIME to "Değiştirilme tarihi",
        ExifInterface.TAG_LENS_MAKE to "Lens üreticisi",
        ExifInterface.TAG_LENS_MODEL to "Lens",
        ExifInterface.TAG_BODY_SERIAL_NUMBER to "Gövde seri no",
        ExifInterface.TAG_LENS_SERIAL_NUMBER to "Lens seri no",
        ExifInterface.TAG_CAMERA_OWNER_NAME to "Kamera sahibi",
        ExifInterface.TAG_ARTIST to "Yazar",
        ExifInterface.TAG_COPYRIGHT to "Telif",
        ExifInterface.TAG_IMAGE_DESCRIPTION to "Açıklama",
        ExifInterface.TAG_USER_COMMENT to "Kullanıcı notu",
        ExifInterface.TAG_IMAGE_UNIQUE_ID to "Görüntü kimliği",
    )

    /** ExifInterface'teki tüm TAG_* sabitleri (yansıtma ile). */
    private val ALL_TAGS: List<String> by lazy {
        ExifInterface::class.java.fields
            .filter {
                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.type == String::class.java && it.name.startsWith("TAG_")
            }
            .mapNotNull { runCatching { it.get(null) as? String }.getOrNull() }
            .distinct()
            .sorted()
    }

    fun analyze(context: Context, uri: Uri): List<Section> {
        val cr = context.contentResolver
        val sections = mutableListOf<Section>()

        // Dosya bilgisi
        var name: String? = null
        var size: Long? = null
        runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        val fileRows = mutableListOf<InfoRow>()
        name?.let { fileRows += InfoRow("Dosya adı", it) }
        size?.let { fileRows += InfoRow("Boyut", humanSize(it)) }
        cr.getType(uri)?.let { fileRows += InfoRow("Tür", it) }

        // Konum etiketlerinin silinmemesi için mümkünse "orijinal" dosyayı iste
        fun open(u: Uri): ExifInterface? = cr.openInputStream(u)?.use { ExifInterface(it) }
        val exif: ExifInterface? = try {
            val original = if (Build.VERSION.SDK_INT >= 29 && uri.authority == MediaStore.AUTHORITY) {
                MediaStore.setRequireOriginal(uri)
            } else {
                uri
            }
            try {
                open(original)
            } catch (e: Exception) {
                open(uri)
            }
        } catch (e: Exception) {
            if (fileRows.isNotEmpty()) sections += Section("Dosya", fileRows)
            sections += Section.fromException("Metadata", e)
            return sections
        }
        if (exif == null) {
            sections += Section.error("Metadata", "Dosya açılamadı")
            return sections
        }

        // Boyutlar
        val w = exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0)
            .takeIf { it > 0 } ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
        val h = exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0)
            .takeIf { it > 0 } ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
        if (w > 0 && h > 0) {
            fileRows += InfoRow("Çözünürlük", "$w × $h (${String.format(Locale.US, "%.1f", w * h / 1_000_000.0)} MP)")
        }
        if (fileRows.isNotEmpty()) sections += Section("Dosya", fileRows)

        // GPS
        val gps = mutableListOf<InfoRow>()
        val latLong = exif.latLong
        if (latLong != null) {
            val lat = String.format(Locale.US, "%.6f", latLong[0])
            val lon = String.format(Locale.US, "%.6f", latLong[1])
            gps += InfoRow("Uyarı", "Bu fotoğraf çekildiği yerin koordinatlarını içeriyor.")
            gps += InfoRow("Koordinat", "$lat, $lon", "https://www.google.com/maps?q=$lat,$lon")
            gps += InfoRow("OpenStreetMap", "Haritada aç", "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=17/$lat/$lon")
            val alt = exif.getAltitude(Double.NaN)
            if (!alt.isNaN()) gps += InfoRow("Yükseklik", "${alt.roundToInt()} m")
            val dir = exif.getAttributeDouble(ExifInterface.TAG_GPS_IMG_DIRECTION, Double.NaN)
            if (!dir.isNaN()) gps += InfoRow("Kamera yönü", "${dir.roundToInt()}° (${compass(dir)})")
            val date = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP)
            val time = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP)
            if (date != null || time != null) gps += InfoRow("GPS zamanı (UTC)", listOfNotNull(date, time).joinToString(" "))
        } else {
            gps += InfoRow("Sonuç", "Konum etiketi yok")
            gps += InfoRow(
                "Not",
                "WhatsApp, Instagram, Telegram gibi uygulamalar konumu siler. Galeriden seçilen fotoğraflarda " +
                    "Android konumu gizleyebilir; uygulamaya fotoğraf/konum izni verip dosyayı tekrar seçin.",
            )
        }
        sections += Section("Konum (GPS)", gps)

        // Öne çıkanlar
        val main = mutableListOf<InfoRow>()
        for ((tag, label) in HIGHLIGHT) clean(exif.getAttribute(tag))?.let { main += InfoRow(label, it) }
        val settings = buildList {
            val f = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
            if (f > 0) add("f/" + String.format(Locale.US, "%.1f", f))
            val exp = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
            if (exp > 0) add(if (exp < 1) "1/${(1 / exp).roundToInt()} sn" else String.format(Locale.US, "%.1f sn", exp))
            exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { add("ISO $it") }
            val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
            if (focal > 0) add(String.format(Locale.US, "%.1f mm", focal))
        }
        if (settings.isNotEmpty()) main += InfoRow("Çekim ayarları", settings.joinToString(" · "))
        if (exif.hasThumbnail()) {
            main += InfoRow("Gömülü küçük resim", "Var — düzenlenmiş fotoğraflarda orijinalin izi kalabilir")
        }
        sections += if (main.isEmpty()) {
            Section("Cihaz ve tarih", listOf(InfoRow("Sonuç", "Bu bilgiler yok (metadata silinmiş olabilir)")))
        } else {
            Section("Cihaz ve tarih", main)
        }

        // Tüm etiketler
        val all = ALL_TAGS.mapNotNull { tag -> clean(exif.getAttribute(tag))?.let { InfoRow(tag, it) } }
        if (all.isNotEmpty()) sections += Section("Tüm etiketler (${all.size})", all)

        return sections
    }

    private fun clean(v: String?): String? {
        val t = v?.trim()?.trimEnd('\u0000')?.trim() ?: return null
        if (t.isEmpty()) return null
        if (t.any { it.isISOControl() && it != '\n' && it != '\t' }) return null // ikili veri
        return t.truncate(300)
    }

    private fun humanSize(b: Long): String = when {
        b >= 1_048_576 -> String.format(Locale.US, "%.1f MB", b / 1_048_576.0)
        b >= 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
        else -> "$b B"
    }

    private fun compass(deg: Double): String {
        val names = listOf("K", "KD", "D", "GD", "G", "GB", "B", "KB")
        return names[(((deg % 360) + 360) % 360 / 45.0).roundToInt() % 8]
    }
}
