package com.rovia.music.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rovia.music.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LyricShareLine(
    val text: String,
    val isGap: Boolean = false,
)

data class LyricShareRequest(
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val lines: List<LyricShareLine>,
)

private const val LYRIC_SHARE_CARD_WIDTH_PX = 1080

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricShareBottomSheet(
    request: LyricShareRequest,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(
            SheetValue.Hidden,
            SheetValue.PartiallyExpanded,
            SheetValue.Expanded,
        ),
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    val shareTitle = stringResource(R.string.lyric_menu_share)

    var busy by remember {
        mutableStateOf(false)
    }

    var saveCompleted by remember {
        mutableStateOf(false)
    }

    val artworkBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = request.artworkUri,
        key2 = context,
    ) {
        value = ArtworkBitmapLoader.loadArtwork(
            context = context,
            artworkUri = request.artworkUri,
            maxDimension = 160,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.lyric_menu_share),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(graphicsLayer)
                    },
            ) {
                LyricShareCard(
                    request = request,
                    artworkBitmap = artworkBitmap,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (busy) return@OutlinedButton
                        busy = true
                        scope.launch {
                            val bitmap = captureLyricShareBitmap(graphicsLayer)
                            val saved = saveLyricShareCard(context, bitmap)
                            busy = false

                            if (saved) {
                                saveCompleted = true
                                delay(1800)
                                saveCompleted = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            if (saveCompleted) {
                                R.string.wrapped_saved
                            } else {
                                R.string.wrapped_save
                            },
                        ),
                    )
                }

                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        scope.launch {
                            val bitmap = captureLyricShareBitmap(graphicsLayer)
                            shareLyricShareCard(context, bitmap, shareTitle)
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.wrapped_share))
                }
            }
        }
    }
}

@Composable
private fun LyricShareCard(
    request: LyricShareRequest,
    artworkBitmap: Bitmap?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
    ) {
        ArtworkBackdrop(
            artworkUri = request.artworkUri,
            foregroundArtworkHeight = 0.dp,
            modifier = Modifier.matchParentSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = artworkBitmap
                        ?.takeUnless(Bitmap::isRecycled)

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = "♫",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = request.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = request.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            request.lines.forEachIndexed { index, line ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (line.isGap) {
                    Text(
                        text = "⋯",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 34.sp,
                            lineHeight = 41.sp,
                            fontWeight = FontWeight.Black,
                        ),
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 34.sp,
                            lineHeight = 41.sp,
                            fontWeight = FontWeight.Black,
                        ),
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.End)
                    .size(24.dp),
            )
        }
    }
}

private suspend fun captureLyricShareBitmap(
    graphicsLayer: GraphicsLayer,
): Bitmap {
    val image = graphicsLayer.toImageBitmap().asAndroidBitmap()

    if (image.width <= LYRIC_SHARE_CARD_WIDTH_PX) {
        return image
    }

    val scale = LYRIC_SHARE_CARD_WIDTH_PX.toFloat() / image.width

    return Bitmap.createScaledBitmap(
        image,
        LYRIC_SHARE_CARD_WIDTH_PX,
        (image.height * scale).toInt().coerceAtLeast(1),
        true,
    )
}


private suspend fun saveLyricShareCard(
    context: Context,
    bitmap: Bitmap,
): Boolean =
    withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "rovia-lyrics-${System.currentTimeMillis()}.png",
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Rovia")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver

        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ) ?: return@withContext false

        val written = resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        } ?: false

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        written
    }

private suspend fun shareLyricShareCard(
    context: Context,
    bitmap: Bitmap,
    chooserTitle: String,
) {
    val uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "lyric-share").apply { mkdirs() }
        val file = File(directory, "rovia-lyrics-${System.currentTimeMillis()}.png")

        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
