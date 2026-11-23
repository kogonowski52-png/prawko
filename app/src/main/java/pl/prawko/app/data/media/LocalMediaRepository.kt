package pl.prawko.app.data.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.LruCache
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.prawko.app.domain.LocalMedia
import pl.prawko.app.domain.MediaRepository
import pl.prawko.app.domain.QuestionMediaReference
import pl.prawko.app.domain.QuestionMediaType

private const val ASSET_URI_PREFIX = "asset:///"
private const val MAX_IMAGE_DIMENSION = 16_384

class LocalMediaRepository(context: Context) : MediaRepository {
    private val appContext = context.applicationContext
    private val cache = MediaPackCache(File(appContext.cacheDir, "catalog-media"), ::parseManifest)
    private val validation = LruCache<String, Boolean>(4_096)
    private val installLock = Mutex()
    private val codecs by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS) }

    override suspend fun resolve(
        datasetVersion: String,
        reference: QuestionMediaReference,
    ): LocalMedia? = withContext(Dispatchers.IO) {
        val path = reference.assetPath ?: return@withContext null
        if (!MediaVersionPattern.matches(datasetVersion)) return@withContext null
        val extension = MediaPathPattern.matchEntire(path)?.groupValues?.get(2)
        val type = when (extension) {
            "jpg" -> QuestionMediaType.IMAGE
            "mp4" -> QuestionMediaType.VIDEO
            else -> return@withContext null
        }
        if (reference.type != type) return@withContext null

        val file = cache.locate(datasetVersion, path)
        if (file != null) {
            val uri = Uri.fromFile(file).toString()
            val stamp = "$uri:${file.length()}:${file.lastModified()}"
            if (usable(uri, type, stamp)) return@withContext LocalMedia(type, uri)
        }

        val bundledUri = "${ASSET_URI_PREFIX}catalog/$path"
        if (usable(bundledUri, type, bundledUri)) LocalMedia(type, bundledUri) else null
    }

    /** Installs an independently verified, immutable pack into the app's evictable cache. */
    suspend fun installPack(
        datasetVersion: String,
        archive: InputStream,
        expectedZipSha256: String,
    ): MediaPackInstallation = withContext(Dispatchers.IO) {
        installLock.withLock {
            cache.install(datasetVersion, archive, expectedZipSha256.trim().lowercase())
        }
    }

    private fun usable(uri: String, type: QuestionMediaType, stamp: String): Boolean {
        validation.get(stamp)?.let { return it }
        val valid = try {
            when (type) {
                QuestionMediaType.IMAGE -> validJpeg(uri)
                QuestionMediaType.VIDEO -> validMp4(uri)
            }
        } catch (_: IOException) {
            false
        } catch (_: RuntimeException) {
            false
        }
        validation.put(stamp, valid)
        return valid
    }

    private fun validJpeg(uri: String): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.openLocalMedia(uri).use { BitmapFactory.decodeStream(it, null, options) }
        return options.outMimeType == "image/jpeg" &&
            options.outWidth in 1..MAX_IMAGE_DIMENSION && options.outHeight in 1..MAX_IMAGE_DIMENSION
    }

    private fun validMp4(uri: String): Boolean {
        appContext.openLocalMedia(uri).use { stream ->
            // Prepared clips are MP4/AVC, with an ISO Base Media File Type box at the start.
            val header = stream.buffered()
            repeat(4) { if (header.read() < 0) return false }
            if (buildString { repeat(4) { append(header.read().toChar()) } } != "ftyp") return false
        }
        val extractor = MediaExtractor()
        try {
            if (uri.startsWith(ASSET_URI_PREFIX)) {
                appContext.assets.openFd(uri.removePrefix(ASSET_URI_PREFIX)).use { asset ->
                    extractor.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                }
            } else {
                extractor.setDataSource(appContext, uri.toUri(), null)
            }
            return (0 until extractor.trackCount).any { index ->
                val format = extractor.getTrackFormat(index)
                format.getString(MediaFormat.KEY_MIME) == "video/avc" &&
                    format.containsKey(MediaFormat.KEY_DURATION) &&
                    format.getLong(MediaFormat.KEY_DURATION) > 0 &&
                    codecs.findDecoderForFormat(format) != null
            }
        } finally {
            extractor.release()
        }
    }
}

internal fun Context.openLocalMedia(uri: String): InputStream {
    if (uri.startsWith(ASSET_URI_PREFIX)) {
        return assets.open(uri.removePrefix(ASSET_URI_PREFIX))
    }

    val local = uri.toUri()
    require(local.scheme == "file") { "Obsługiwane są tylko lokalne pliki multimedialne." }
    return contentResolver.openInputStream(local) ?: throw IOException("Plik jest niedostępny.")
}

internal fun parseManifest(json: String): MediaPackManifest {
    val manifest = JSONObject(json)
    val files = manifest.getJSONArray("files")
    val entries = List(files.length()) { index ->
        val item = files.getJSONObject(index)
        val size = item.get("sizeBytes")
        require(size is Int || size is Long) { "Rozmiar pliku musi być liczbą całkowitą." }
        MediaPackFile(item.getString("path"), (size as Number).toLong())
    }
    return MediaPackManifest(
        (manifest.get("schemaVersion") as? Int) ?: throw IllegalArgumentException("Nieprawidłowa wersja manifestu."),
        manifest.getString("datasetVersion"),
        manifest.getString("packId"),
        entries,
    )
}
