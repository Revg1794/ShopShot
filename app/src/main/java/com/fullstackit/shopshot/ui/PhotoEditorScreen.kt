package com.fullstackit.shopshot.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fullstackit.shopshot.data.CropFraming
import com.fullstackit.shopshot.data.ImageEditor
import com.fullstackit.shopshot.data.Overlay
import com.fullstackit.shopshot.data.OverlayRenderer
import com.fullstackit.shopshot.data.Prefs
import com.fullstackit.shopshot.data.Shot
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

private enum class CropShape(val label: String, val ratio: Float?) {
    Original("Original", null),
    Square("1:1", 1f),
    Portrait45("4:5", 4f / 5f),
    Portrait34("3:4", 3f / 4f),
}

private enum class EditorMode(val label: String) { Crop("Crop"), Text("Text"), Logo("Logo") }

private val TEXT_COLOURS = listOf(
    Color.White,
    Color.Black,
    Color(0xFFFFD54F),
    Color(0xFFEF5350),
    Color(0xFF42A5F5),
    Color(0xFF66BB6A),
)

@Composable
fun PhotoEditorScreen(
    shot: Shot,
    onCancel: () -> Unit,
    onSave: (CropFraming, List<Overlay>) -> Unit,
) {
    val context = LocalContext.current
    val editor = remember { ImageEditor(context) }
    val renderer = remember { OverlayRenderer(context) }
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onCancel)

    var source by remember(shot.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(shot.id) { mutableStateOf(false) }
    var quarterTurns by remember(shot.id) { mutableStateOf(0) }
    var shape by remember(shot.id) { mutableStateOf(CropShape.Original) }
    var scale by remember(shot.id) { mutableStateOf(1f) }
    var offsetX by remember(shot.id) { mutableStateOf(0f) }
    var offsetY by remember(shot.id) { mutableStateOf(0f) }

    var mode by remember(shot.id) { mutableStateOf(EditorMode.Crop) }
    var overlays by remember(shot.id) { mutableStateOf<List<Overlay>>(emptyList()) }
    var selectedId by remember(shot.id) { mutableStateOf<Long?>(null) }
    var editingText by remember(shot.id) { mutableStateOf<Overlay.Text?>(null) }

    LaunchedEffect(shot.id) {
        val loaded = editor.loadForDisplay(shot.uri)
        if (loaded == null) failed = true else source = loaded
    }

    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked: Uri? ->
        if (picked == null) return@rememberLauncherForActivityResult
        // importWatermark does its work off the main thread.
        scope.launch {
            val stored = editor.importWatermark(picked)
            if (stored != null) {
                prefs.watermarkPath = stored.toString()
                renderer.forget(stored)
                val item = Overlay.Logo(id = System.nanoTime(), uri = stored)
                overlays = overlays + item
                selectedId = item.id
            }
        }
    }

    val rotated = remember(source, quarterTurns) {
        val base = source ?: return@remember null
        if (quarterTurns % 4 == 0) base else rotateBitmap(base, quarterTurns)
    }
    val selected = overlays.firstOrNull { it.id == selectedId }

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
                        ),
                        overlays,
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
                    "Could not open this photo",
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

                        val coverScale = max(winW / bitmap.width, winH / bitmap.height)
                        val maxOffX =
                            max(0f, (bitmap.width * coverScale * scale - winW) / 2f) / winW
                        val maxOffY =
                            max(0f, (bitmap.height * coverScale * scale - winH) / 2f) / winH

                        LaunchedEffect(maxOffX, maxOffY) {
                            offsetX = offsetX.coerceIn(-maxOffX, maxOffX)
                            offsetY = offsetY.coerceIn(-maxOffY, maxOffY)
                        }

                        val density = LocalDensity.current
                        Box(
                            modifier = Modifier
                                .width(with(density) { winW.toDp() })
                                .height(with(density) { winH.toDp() })
                                .clipToBounds()
                                .border(1.dp, Color.White.copy(alpha = 0.7f))
                                .pointerInput(bitmap, shape, mode) {
                                    if (mode != EditorMode.Crop) return@pointerInput
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val next = (scale * zoom).coerceIn(1f, 8f)
                                        scale = next
                                        val mx = max(
                                            0f,
                                            (bitmap.width * coverScale * next - winW) / 2f
                                        ) / winW
                                        val my = max(
                                            0f,
                                            (bitmap.height * coverScale * next - winH) / 2f
                                        ) / winH
                                        offsetX = (offsetX + pan.x / winW).coerceIn(-mx, mx)
                                        offsetY = (offsetY + pan.y / winH).coerceIn(-my, my)
                                    }
                                }
                                .pointerInput(mode, overlays.size) {
                                    if (mode == EditorMode.Crop) return@pointerInput
                                    detectTapGestures { tap ->
                                        selectedId = nearestOverlay(
                                            overlays, tap, winW, winH,
                                        )?.id
                                    }
                                }
                                .pointerInput(mode, selectedId) {
                                    if (mode == EditorMode.Crop) return@pointerInput
                                    detectDragGestures { _, drag ->
                                        val id = selectedId ?: return@detectDragGestures
                                        overlays = overlays.map { item ->
                                            if (item.id != id) item else item.movedBy(
                                                drag.x / winW,
                                                drag.y / winH,
                                            )
                                        }
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

                            if (mode == EditorMode.Crop) CropGuides()

                            // Drawn through the same renderer the export uses, so the preview
                            // cannot drift from the saved result.
                            Canvas(Modifier.fillMaxSize()) {
                                drawIntoCanvas { canvas ->
                                    renderer.draw(
                                        canvas.nativeCanvas,
                                        size.width.toInt(),
                                        size.height.toInt(),
                                        overlays,
                                    )
                                }
                            }

                            selected?.let { item ->
                                SelectionMarker(item, winW, winH)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EditorMode.entries.forEach { option ->
                Chip(
                    label = option.label,
                    selected = option == mode,
                    onClick = {
                        mode = option
                        if (option == EditorMode.Crop) selectedId = null
                    },
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(96.dp)) {
            when (mode) {
                EditorMode.Crop -> CropControls(
                    shape = shape,
                    onShape = {
                        shape = it
                        scale = 1f; offsetX = 0f; offsetY = 0f
                    },
                    onRotate = {
                        quarterTurns = (quarterTurns + 1) % 4
                        scale = 1f; offsetX = 0f; offsetY = 0f
                    },
                )

                EditorMode.Text -> TextControls(
                    selected = selected as? Overlay.Text,
                    onAdd = { editingText = Overlay.Text(id = System.nanoTime(), text = "") },
                    onEdit = { editingText = it },
                    onSize = { size ->
                        overlays = overlays.map {
                            if (it.id == selectedId && it is Overlay.Text) it.copy(sizeFraction = size)
                            else it
                        }
                    },
                    onColour = { colour ->
                        overlays = overlays.map {
                            if (it.id == selectedId && it is Overlay.Text) {
                                it.copy(colorArgb = colour.toArgb())
                            } else it
                        }
                    },
                    onDelete = {
                        overlays = overlays.filterNot { it.id == selectedId }
                        selectedId = null
                    },
                )

                EditorMode.Logo -> LogoControls(
                    selected = selected as? Overlay.Logo,
                    hasSaved = prefs.watermarkPath != null,
                    onPick = {
                        logoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onUseSaved = {
                        val stored = prefs.watermarkPath?.let(Uri::parse)
                        if (stored != null) {
                            val item = Overlay.Logo(id = System.nanoTime(), uri = stored)
                            overlays = overlays + item
                            selectedId = item.id
                        }
                    },
                    onSize = { size ->
                        overlays = overlays.map {
                            if (it.id == selectedId && it is Overlay.Logo) it.copy(sizeFraction = size)
                            else it
                        }
                    },
                    onAlpha = { alpha ->
                        overlays = overlays.map {
                            if (it.id == selectedId && it is Overlay.Logo) it.copy(alpha = alpha)
                            else it
                        }
                    },
                    onDelete = {
                        overlays = overlays.filterNot { it.id == selectedId }
                        selectedId = null
                    },
                )
            }
        }
    }

    editingText?.let { draft ->
        TextEntryDialog(
            initial = draft.text,
            onDismiss = { editingText = null },
            onConfirm = { typed ->
                overlays = if (overlays.any { it.id == draft.id }) {
                    overlays.map {
                        if (it.id == draft.id && it is Overlay.Text) it.copy(text = typed) else it
                    }
                } else {
                    overlays + draft.copy(text = typed)
                }
                selectedId = draft.id
                editingText = null
            },
        )
    }
}

@Composable
private fun CropControls(
    shape: CropShape,
    onShape: (CropShape) -> Unit,
    onRotate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CropShape.entries.forEach { option ->
            Chip(option.label, option == shape) { onShape(option) }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRotate) {
            Icon(Icons.Filled.Rotate90DegreesCcw, contentDescription = "Rotate", tint = Color.White)
        }
    }
}

@Composable
private fun TextControls(
    selected: Overlay.Text?,
    onAdd: () -> Unit,
    onEdit: (Overlay.Text) -> Unit,
    onSize: (Float) -> Unit,
    onColour: (Color) -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        if (selected == null) {
            Chip("Add text", selected = false, onClick = onAdd)
            Text(
                "Tap a label on the photo to change it",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip("Edit", selected = false) { onEdit(selected) }
                Spacer(Modifier.width(8.dp))
                TEXT_COLOURS.forEach { colour ->
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(colour)
                            .border(
                                width = if (colour.toArgb() == selected.colorArgb) 2.dp else 1.dp,
                                color = Color.White.copy(alpha = 0.8f),
                                shape = CircleShape,
                            )
                            .clickable { onColour(colour) },
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Remove label",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Slider(
                value = selected.sizeFraction,
                onValueChange = onSize,
                valueRange = 0.03f..0.3f,
            )
        }
    }
}

@Composable
private fun LogoControls(
    selected: Overlay.Logo?,
    hasSaved: Boolean,
    onPick: () -> Unit,
    onUseSaved: () -> Unit,
    onSize: (Float) -> Unit,
    onAlpha: (Float) -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        if (selected == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasSaved) Chip("Use my logo", selected = false, onClick = onUseSaved)
                Chip(if (hasSaved) "Choose another" else "Choose logo", false, onPick)
            }
            Text(
                "A logo you pick is kept, so you only choose it once",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Size", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = selected.sizeFraction,
                    onValueChange = onSize,
                    valueRange = 0.08f..0.8f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Remove logo",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fade", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = selected.alpha,
                    onValueChange = onAlpha,
                    valueRange = 0.15f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TextEntryDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Label") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text("Size 10, worn once...") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text("Done")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A dashed ring around whatever is selected, so it is obvious what the sliders will change. */
@Composable
private fun SelectionMarker(item: Overlay, winW: Float, winH: Float) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                translationX = item.centreX * winW - 14.dp.toPx()
                translationY = item.centreY * winH - 14.dp.toPx()
            }
            .border(2.dp, Color.White, CircleShape),
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
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

@Composable
private fun CropGuides() {
    Canvas(Modifier.fillMaxSize()) {
        val line = Color.White.copy(alpha = 0.35f)
        val stroke = 1.dp.toPx()
        listOf(1f / 3f, 2f / 3f).forEach { f ->
            drawLine(line, Offset(size.width * f, 0f), Offset(size.width * f, size.height), stroke)
            drawLine(line, Offset(0f, size.height * f), Offset(size.width, size.height * f), stroke)
        }
    }
}

private fun Overlay.movedBy(dx: Float, dy: Float): Overlay = when (this) {
    is Overlay.Text -> copy(
        centreX = (centreX + dx).coerceIn(0f, 1f),
        centreY = (centreY + dy).coerceIn(0f, 1f),
    )

    is Overlay.Logo -> copy(
        centreX = (centreX + dx).coerceIn(0f, 1f),
        centreY = (centreY + dy).coerceIn(0f, 1f),
    )
}

private fun nearestOverlay(
    overlays: List<Overlay>,
    tap: Offset,
    winW: Float,
    winH: Float,
): Overlay? = overlays.minByOrNull { item ->
    abs(item.centreX * winW - tap.x) + abs(item.centreY * winH - tap.y)
}

private fun rotateBitmap(bitmap: Bitmap, quarterTurns: Int): Bitmap {
    val turns = ((quarterTurns % 4) + 4) % 4
    if (turns == 0) return bitmap
    val matrix = Matrix().apply { postRotate(90f * turns) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
