package pl.prawko.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.prawko.app.domain.QuestionMediaReference
import pl.prawko.app.domain.QuestionMediaRole
import pl.prawko.app.domain.QuestionMediaType

@RunWith(AndroidJUnit4::class)
class LocalMediaRepositoryTest {
    @Test
    fun verifiedPackResolvesLocalJpegAndPlaysSyntheticMp4() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val version = "DEMO-media-${UUID.randomUUID()}"
        val jpeg = ByteArrayOutputStream().use { output ->
            val image = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            try {
                image.eraseColor(Color.BLUE)
                check(image.compress(Bitmap.CompressFormat.JPEG, 85, output))
                output.toByteArray()
            } finally {
                image.recycle()
            }
        }
        val video = instrumentation.context.assets.open("demo-clip.mp4").use { it.readBytes() }
        val jpegRef = reference(jpeg, "DEMO.jpg", "jpg", QuestionMediaType.IMAGE)
        val videoRef = reference(video, "DEMO.mp4", "mp4", QuestionMediaType.VIDEO)
        val files = mapOf(requireNotNull(jpegRef.assetPath) to jpeg, requireNotNull(videoRef.assetPath) to video)
        val archive = pack(version, files)
        val repository = LocalMediaRepository(context)
        try {
            assertNull(repository.resolve(version, videoRef))
            assertFalse(runCatching { repository.installPack(version, archive.inputStream(), "0".repeat(64)) }.isSuccess)
            assertNull(repository.resolve(version, jpegRef))

            val installation = repository.installPack(version, archive.inputStream(), hash(archive))
            assertEquals(2, installation.fileCount)
            val reloaded = LocalMediaRepository(context)
            val image = requireNotNull(reloaded.resolve(version, jpegRef))
            val clip = requireNotNull(reloaded.resolve(version, videoRef))
            assertEquals(QuestionMediaType.IMAGE, image.type)
            assertEquals(QuestionMediaType.VIDEO, clip.type)
            assertNull(reloaded.resolve("DEMO-other-version", jpegRef))
            assertNull(reloaded.resolve(version, videoRef.copy(assetPath = videoRef.assetPath?.replace(".mp4", ".wmv"))))
            assertNull(reloaded.resolve(version, videoRef.copy(assetPath = null)))

            val finished = CountDownLatch(1)
            val rendered = AtomicBoolean(false)
            val error = AtomicReference<PlaybackException?>(null)
            lateinit var player: ExoPlayer
            lateinit var texture: SurfaceTexture
            lateinit var surface: Surface
            instrumentation.runOnMainSync {
                texture = SurfaceTexture(0)
                surface = Surface(texture)
                player = ExoPlayer.Builder(context).build().apply {
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            rendered.set(true)
                        }

                        override fun onPlayerError(playbackException: PlaybackException) {
                            error.set(playbackException)
                            finished.countDown()
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) finished.countDown()
                        }
                    })
                    setVideoSurface(surface)
                    setMediaItem(MediaItem.fromUri(clip.uri))
                    prepare()
                    play()
                }
            }
            try {
                assertTrue("Synthetic clip did not finish within 90 seconds", finished.await(90, TimeUnit.SECONDS))
                assertNull(error.get())
                assertTrue("Media3 never rendered a frame", rendered.get())
            } finally {
                instrumentation.runOnMainSync {
                    player.release()
                    surface.release()
                    texture.release()
                }
            }
        } finally {
            File(context.cacheDir, "catalog-media/$version").deleteRecursively()
        }
        Unit
    }

    private fun reference(bytes: ByteArray, name: String, suffix: String, type: QuestionMediaType) =
        QuestionMediaReference(QuestionMediaRole.QUESTION, name, "media/${hash(bytes)}.$suffix", type)

    private fun pack(version: String, files: Map<String, ByteArray>): ByteArray {
        val entries = JSONArray().apply {
            files.forEach { (path, bytes) -> put(JSONObject().put("path", path).put("sizeBytes", bytes.size)) }
        }
        val manifest = JSONObject().put("schemaVersion", 1).put("datasetVersion", version)
            .put("packId", "DEMO-instrumented-pack").put("files", entries)
            .toString().toByteArray()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((path, bytes) in mapOf("manifest.json" to manifest) + files) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
