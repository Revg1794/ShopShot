package com.fullstackit.shopshot.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fullstackit.shopshot.data.CropFraming
import com.fullstackit.shopshot.data.ImageEditor
import com.fullstackit.shopshot.data.Shot
import kotlin.math.max

/** Shapes offered in the editor. Marketplaces mostly want square or portrait. */
private enum class CropShape(val label: String, val ratio: Float?) {
    Original("Original", null),
    Square("1:1", 1f),
    Portrait45("4:5", 4f / 5f),
    Portrait34("3:4", 3f / 4f),
}

@Composable
fun PhotoEditorScreen(
    shot: Shot,
    onCancel: () -> Unit,
    onSave: (CropFraming) -> Unit,
) {
    val context = LocalContext.current
    val editor = remember { ImageEditor(context) }

    BackHandler(onBack = onCancel)

    var source by remember(shot.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(shot.id) { mutableStateOf(false) }
    var quarterTurns by remember(shot.id) { mutableStateOf(0) }
    var shape by remember(shot.id) { mutableStateOf(CropShape.Original) }
    var scale by remember(shot.id) { mutableStateOf(1f) }
    var offsetX by remember(shot.id) { mutableStateOf(0f) }
    var offsetY by remember(shot.id) { mutableStateOf(0f) }

    LaunchedEffect(shot.id) {
        val loaded = editor.loadForDisplay(shot.uri)
        if (loaded == null) failed = true else source = loaded
    }

    // The preview works on an already-rotated bitmap rather than rotating in the graphics
    // layer, so the cover-fit maths here is identical to the maths used for the export.
    val rotated = remember(source, quarterTurns) {
        val base = source ?: return@remember null
        if (quarterTurns % 4 == 0) base else rotateBitmap(base, quarterTurns)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
            }
            Text(
                text = "Edit photo",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    onSave(
                        CropFraming(
                            scale = scale,
                            offsetXFraction = offsetX,
                            offsetYFraction = offsetY,
                            quarterTurns = quarterTurns,
                            aspectRatio = shape.ratio,
                        )
                    )
                },
                enabled = rotated != null,
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                failed -> Text(
                    text = "Could not open this photo",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                )

                rotated == null -> CircularProgressIndicator(color = Color.White)

                else -> {
                    val bitmap = rotated
                    val windowAspect = shape.ratio ?: (bitmap.width.toFloat() / bitmap.height)

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Largest window of the requested shape that fits the space available.
                        val availW = constraints.maxWidth.toFloat()
                        val availH = constraints.maxHeight.toFloat()
                        val winW: Float
                        val winH: Float
                        if (availW / availH > windowAspect) {
                            winH = availH
                            winW = availH * windowAspect
                        } else {
                            winW = availW
                            winH = availW / windowAspect
                        }

                        // Cover fit, matching ImageEditor.render.
                        val coverScale = max(winW / bitmap.width, winH / bitmap.height)
                        val drawnW = bitmap.width * coverScale * scale
                        val drawnH = bitmap.height * coverScale * scale
                        // Pan is limited so the window can never show past the photo's edge.
                        val maxOffX = max(0f, (drawnW - winW) / 2f) / winW
                        val maxOffY = max(0f, (drawnH - winH) / 2f) / winH

                        LaunchedEffect(maxOffX, maxOffY) {
                            offsetX = offsetX.coerceIn(-maxOffX, maxOffX)
                            offsetY = offsetY.coerceIn(-maxOffY, maxOffY)
                        }

                        Box(
                            modifier = Modifier
                                .width(with(androidx.compose.ui.platform.LocalDensity.current) { winW.toDp() })
                                .height(with(androidx.compose.ui.platform.LocalDensity.current) { winH.toDp() })
                                .clipToBounds()
                                .border(1.dp, Color.White.copy(alpha = 0.7f))
                                .pointerInput(bitmap, shape) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val next = (scale * zoom).coerceIn(1f, 8f)
                                        scale = next
                                        val dW = bitmap.width * coverScale * next
                                        val dH = bitmap.height * coverScale * next
                                        val mx = max(0f, (dW - winW) / 2f) / winW
                                        val my = max(0f, (dH - winH) / 2f) / winH
                                        offsetX = (offsetX + pan.x / winW).coerceIn(-mx, mx)
                                        offsetY = (offsetY + pan.y / winH).coerceIn(-my, my)
                                    }
                                },
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = shot.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offsetX * winW
                                        translationY = offsetY * winH
                                    },
                            )
                            CropGuides()
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CropShape.entries.forEach { option ->
                ShapeChip(
                    label = option.label,
                    selected = option == shape,
                    onClick = {
                        shape = option
                        // A new window shape invalidates the old framing, so start clean
                        // rather than leaving the photo at an offset that no longer fits.
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    quarterTurns = (quarterTurns + 1) % 4
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                },
            ) {
                Icon(
                    Icons.Filled.Rotate90DegreesCcw,
                    contentDescription = "Rotate",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ShapeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.12f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Rule-of-thirds lines, the usual cue that this area is what gets kept. */
@Composable
private fun CropGuides() {
    Canvas(Modifier.fillMaxSize()) {
        val line = Color.White.copy(alpha = 0.35f)
        val stroke = 1.dp.toPx()
        listOf(1f / 3f, 2f / 3f).forEach { f ->
            drawLine(
                color = line,
                start = Offset(size.width * f, 0f),
                end = Offset(size.width * f, size.height),
                strokeWidth = stroke,
            )
            drawLine(
                color = line,
                start = Offset(0f, size.height * f),
                end = Offset(size.width, size.height * f),
                strokeWidth = stroke,
            )
        }
    }
}

private fun rotateBitmap(bitmap: Bitmap, quarterTurns: Int): Bitmap {
    val turns = ((quarterTurns % 4) + 4) % 4
    if (turns == 0) return bitmap
    val matrix = Matrix().apply { postRotate(90f * turns) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
