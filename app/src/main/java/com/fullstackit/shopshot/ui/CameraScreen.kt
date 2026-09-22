package com.fullstackit.shopshot.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.fullstackit.shopshot.data.Shot
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun CameraScreen(
    state: UiState,
    vm: AppViewModel,
    onOpenFolders: () -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    var sheetOpen by remember { mutableStateOf(false) }
    // When "ask every shot" is on, the picker opens first and the capture waits for the answer.
    var captureAfterPick by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var shutterFlash by remember { mutableStateOf(false) }

    // Bind (or rebind, when the lens flips) the camera use cases.
    LaunchedEffect(state.useFrontCamera) {
        val cameraProvider = provider ?: awaitCameraProvider(context).also { provider = it }
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }
        val selector = if (state.useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        runCatching {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        }
    }

    // Flash is a live property, so it does not need a rebind.
    LaunchedEffect(state.flashMode) { imageCapture.flashMode = state.flashMode }

    DisposableEffect(Unit) {
        onDispose { provider?.unbindAll() }
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(900)
            focusPoint = null
        }
    }
    LaunchedEffect(shutterFlash) {
        if (shutterFlash) {
            delay(120)
            shutterFlash = false
        }
    }

    fun capture(target: String) {
        if (capturing) return
        capturing = true
        shutterFlash = true
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        val values = vm.imageValuesForCurrentFolder(target)
        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = state.useFrontCamera
        }
        val options = ImageCapture.OutputFileOptions
            .Builder(context.contentResolver, vm.imageCollection(), values)
            .setMetadata(metadata)
            .build()

        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    vm.onCaptured(output.savedUri, target)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    vm.reportError("Could not save photo: ${exception.message}")
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures { offset ->
                        focusPoint = offset
                        val cam = camera ?: return@detectTapGestures
                        val factory = previewView.meteringPointFactory
                        val point = factory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        runCatching { cam.cameraControl.startFocusAndMetering(action) }
                    }
                }
                .pointerInput(camera) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val cam = camera ?: return@detectTransformGestures
                        val current = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                        runCatching { cam.cameraControl.setZoomRatio(current * zoom) }
                    }
                },
        )

        focusPoint?.let { point -> FocusRing(point) }

        // Brief white wash on capture so she gets feedback even with the shutter sound off.
        val flashAlpha by animateFloatAsState(
            targetValue = if (shutterFlash) 0.7f else 0f,
            animationSpec = tween(120),
            label = "shutterFlash",
        )
        if (flashAlpha > 0f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))
        }

        TopBar(
            state = state,
            onFolderClick = { captureAfterPick = false; sheetOpen = true },
            onFlashClick = vm::cycleFlash,
            onLibraryClick = onOpenFolders,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        BottomBar(
            state = state,
            capturing = capturing,
            onShutter = {
                if (state.askEveryShot) {
                    captureAfterPick = true
                    sheetOpen = true
                } else {
                    capture(state.currentFolder)
                }
            },
            onFlipLens = vm::toggleLens,
            onOpenCurrentFolder = { onOpenFolder(state.currentFolder) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (sheetOpen) {
        FolderPickerSheet(
            state = state,
            title = if (captureAfterPick) "Save this photo to" else "Shooting into",
            onDismiss = {
                sheetOpen = false
                captureAfterPick = false
            },
            onPick = { name ->
                sheetOpen = false
                vm.selectFolder(name)
                if (captureAfterPick) {
                    captureAfterPick = false
                    capture(name)
                }
            },
            onCreate = { name ->
                sheetOpen = false
                val created = vm.createFolder(name)
                if (captureAfterPick) {
                    captureAfterPick = false
                    capture(created)
                }
            },
            onToggleAsk = vm::setAskEveryShot,
        )
    }
}

@Composable
private fun TopBar(
    state: UiState,
    onFolderClick: () -> Unit,
    onFlashClick: () -> Unit,
    onLibraryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassIconButton(onClick = onFlashClick) {
            Icon(
                imageVector = when (state.flashMode) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                    ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                    else -> Icons.Filled.FlashOff
                },
                contentDescription = "Flash",
                tint = Color.White,
            )
        }

        // The destination chip is deliberately the biggest thing on screen after the preview:
        // knowing where the next shot lands is the entire point of the app.
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onFolderClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = if (state.askEveryShot) "Asking every shot" else "Saving to",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Text(
                    text = state.currentFolder,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ExpandMore, contentDescription = "Change folder", tint = Color.White)
        }

        GlassIconButton(onClick = onLibraryClick) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = "All folders", tint = Color.White)
        }
    }
}

@Composable
private fun BottomBar(
    state: UiState,
    capturing: Boolean,
    onShutter: () -> Unit,
    onFlipLens: () -> Unit,
    onOpenCurrentFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(WindowInsets.navigationBars.asPaddingValues())
            .padding(bottom = 12.dp),
    ) {
        AnimatedVisibility(
            visible = state.sessionShots.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Column {
                Text(
                    text = "${state.sessionShots.size} this session  ·  " +
                        "${state.currentFolderCount} in ${state.currentFolder}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp),
                )
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    items(state.sessionShots, key = { it.id }) { shot ->
                        SessionThumb(shot = shot, onClick = onOpenCurrentFolder)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.width(64.dp))
            ShutterButton(enabled = !capturing, onClick = onShutter)
            Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                GlassIconButton(onClick = onFlipLens) {
                    Icon(
                        Icons.Filled.Cameraswitch,
                        contentDescription = "Switch camera",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionThumb(shot: Shot, onClick: () -> Unit) {
    AsyncImage(
        model = shot.uri,
        contentDescription = shot.displayName,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.9f,
        animationSpec = tween(120),
        label = "shutterScale",
    )
    Box(
        modifier = Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size((62 * scale).dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun GlassIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun FocusRing(point: Offset) {
    // offset (not padding) so a tap near the top or left edge can position negatively
    // instead of throwing on a negative padding value.
    Box(
        modifier = Modifier
            .offset {
                val half = 34.dp.roundToPx()
                IntOffset(point.x.toInt() - half, point.y.toInt() - half)
            }
            .size(68.dp)
            .border(1.5.dp, Color.White, CircleShape),
    )
}

/** CameraX hands back a ListenableFuture; this is the coroutine-shaped version of it. */
private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { cont.resume(it) }
                    .onFailure { cont.resumeWithException(it) }
            },
            ContextCompat.getMainExecutor(context),
        )
    }
