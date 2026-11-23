package pl.prawko.app.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.view.View
import androidx.annotation.OptIn
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.prawko.app.data.media.openLocalMedia
import pl.prawko.app.domain.LocalMedia
import pl.prawko.app.domain.MediaRepository
import pl.prawko.app.domain.Question
import pl.prawko.app.domain.QuestionMediaType
import pl.prawko.app.domain.primaryMediaReference

@Composable
fun QuestionMediaContent(
    question: Question,
    repository: MediaRepository,
    modifier: Modifier = Modifier,
    controls: Boolean = true,
    playing: Boolean = false,
    videoStartPositionMs: Long = 0L,
    onUnavailable: () -> Unit = {},
) {
    val reference = question.primaryMediaReference ?: return
    key(question.datasetVersion, question.id, reference, repository) {
        var waiting by remember { mutableStateOf(true) }
        var media by remember { mutableStateOf<LocalMedia?>(null) }
        var failed by remember { mutableStateOf(false) }
        val notifyUnavailable by rememberUpdatedState(onUnavailable)

        LaunchedEffect(Unit) {
            media = repository.resolve(question.datasetVersion, reference)
            waiting = false
            failed = media == null
        }
        LaunchedEffect(failed) {
            if (failed) notifyUnavailable()
        }

        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            when {
                failed -> Text(
                    "Materiał do pytania jest niedostępny. Sama treść może nie wystarczyć do oceny sytuacji.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                waiting -> MediaLoading()
                else -> media?.let { resolved ->
                    when (resolved.type) {
                        QuestionMediaType.IMAGE -> QuestionImage(resolved.uri) { failed = true }
                        QuestionMediaType.VIDEO -> QuestionVideo(
                            uri = resolved.uri,
                            controls = controls,
                            playing = playing,
                            startPositionMs = videoStartPositionMs,
                            onFailure = { failed = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics { contentDescription = "Wczytywanie materiału do pytania" },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

private sealed interface ImageState {
    data object Loading : ImageState
    data object Failed : ImageState
    data class Ready(val bitmap: Bitmap) : ImageState
}

@Composable
private fun QuestionImage(uri: String, onFailure: () -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageState>(ImageState.Loading, uri, context) {
        val bitmap = withContext(Dispatchers.IO) { decodeJpeg(context, uri) }
        value = if (bitmap == null) ImageState.Failed else ImageState.Ready(bitmap)
    }
    val reportFailure by rememberUpdatedState(onFailure)
    when (val displayed = image) {
        ImageState.Loading -> MediaLoading()
        ImageState.Failed -> LaunchedEffect(Unit) { reportFailure() }
        is ImageState.Ready -> Image(
            bitmap = displayed.bitmap.asImageBitmap(),
            contentDescription = "Ilustracja sytuacji do pytania",
            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

private fun decodeJpeg(context: Context, uri: String): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.openLocalMedia(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outMimeType != "image/jpeg") return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1_600) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.openLocalMedia(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val orientation = context.openLocalMedia(uri).use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        orientBitmap(bitmap, orientation)
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}

private fun orientBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

@OptIn(UnstableApi::class)
@Composable
private fun QuestionVideo(
    uri: String,
    controls: Boolean,
    playing: Boolean,
    startPositionMs: Long,
    onFailure: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var inForeground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val failure by rememberUpdatedState(onFailure)
    val player = remember(context, uri) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            repeatMode = Player.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(uri), startPositionMs.coerceAtLeast(0L))
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            inForeground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failure()
            }
        }
        player.addListener(listener)
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, controls, playing, inForeground) {
        if (!inForeground) player.pause() else if (!controls) player.playWhenReady = playing
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                setBackgroundColor(Color.BLACK)
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            }
        },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        update = { view ->
            view.player = player
            view.useController = controls
            view.isFocusable = controls
            view.importantForAccessibility = if (controls) {
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        },
        onRelease = { it.player = null },
    )
}
