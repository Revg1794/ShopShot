package com.fullstackit.shopshot.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fullstackit.shopshot.data.Shot

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderDetailScreen(
    folderName: String,
    state: UiState,
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val shots = remember(state.shots, folderName) {
        state.shots.filter { it.folder == folderName }
    }

    var selected by remember { mutableStateOf(setOf<Long>()) }
    var movePickerOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Shot?>(null) }

    val selecting = selected.isNotEmpty()
    val selectedShots = remember(selected, shots) { shots.filter { it.id in selected } }

    // A photo removed elsewhere must not stay selected, or the action bar acts on nothing.
    val liveIds = remember(shots) { shots.map { it.id }.toSet() }
    LaunchedEffect(liveIds) {
        if (selected.any { it !in liveIds }) selected = selected.intersect(liveIds)
    }

    fun share(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share ${photoCount(uris.size)}"))
    }

    // The viewer takes over the whole screen rather than opening in a Dialog. A Compose Dialog
    // does not reliably receive navigation bar insets, so every inset modifier inside one
    // measured as zero and the action row stayed pinned under the navigation bar. In the
    // activity's own window, which is already edge to edge, the insets are correct.
    val viewingShot = viewing
    if (viewingShot != null) {
        PhotoViewerScreen(
            shot = viewingShot,
            onDismiss = { viewing = null },
            onShare = { share(listOf(viewingShot.uri)) },
            onMove = {
                viewing = null
                selected = setOf(viewingShot.id)
                movePickerOpen = true
            },
            onTrash = {
                viewing = null
                vm.trash(listOf(viewingShot.uri))
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (selecting) "${selected.size} selected" else folderName,
                            maxLines = 1,
                        )
                        if (!selecting) {
                            Text(
                                text = countLabel(shots.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (selecting) selected = emptySet() else onBack() }) {
                        Icon(
                            imageVector = if (selecting) Icons.Filled.Close
                            else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selecting) "Clear selection" else "Back",
                        )
                    }
                },
                actions = {
                    if (selecting) {
                        TextButton(onClick = { selected = shots.map { it.id }.toSet() }) {
                            Text("All")
                        }
                    } else {
                        IconButton(onClick = { vm.selectFolder(folderName); onBack() }) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = "Shoot into this folder")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Folder options")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename folder") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.DriveFileRenameOutline,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = { menuOpen = false; renaming = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete folder") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = { menuOpen = false; confirmingDelete = true },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            if (selecting) {
                BottomAppBar(containerColor = MaterialTheme.colorScheme.surface) {
                    IconButton(onClick = { movePickerOpen = true }) {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move")
                    }
                    Text("Move", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { share(selectedShots.map { it.uri }) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    Text("Share", style = MaterialTheme.typography.labelLarge)
                    IconButton(
                        onClick = {
                            vm.trash(selectedShots.map { it.uri })
                            selected = emptySet()
                        }
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove")
                    }
                    Text("Remove", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (shots.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing in this folder yet.\nTap the camera icon above to shoot into it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(shots, key = { it.id }) { shot ->
                    PhotoCell(
                        shot = shot,
                        selected = shot.id in selected,
                        selecting = selecting,
                        onClick = {
                            if (selecting) {
                                selected = if (shot.id in selected) selected - shot.id
                                else selected + shot.id
                            } else {
                                viewing = shot
                            }
                        },
                        onLongClick = { selected = selected + shot.id },
                    )
                }
            }
        }
    }

    if (movePickerOpen) {
        FolderPickerSheet(
            state = state,
            title = "Move ${photoCount(selected.size)} to",
            onDismiss = { movePickerOpen = false },
            onPick = { name ->
                movePickerOpen = false
                vm.move(selectedShots.map { it.uri }, name)
                selected = emptySet()
            },
            onCreate = { name ->
                movePickerOpen = false
                vm.move(selectedShots.map { it.uri }, name)
                selected = emptySet()
            },
            showAskToggle = false,
        )
    }

    if (confirmingDelete) {
        DeleteFolderDialog(
            folderName = folderName,
            photoCountInFolder = shots.size,
            onDismiss = { confirmingDelete = false },
            onConfirm = {
                confirmingDelete = false
                vm.deleteFolder(folderName)
                onBack()
            },
        )
    }

    if (renaming) {
        RenameDialog(
            current = folderName,
            onDismiss = { renaming = false },
            onConfirm = { newName ->
                renaming = false
                vm.renameFolder(folderName, newName)
                onBack()
            },
        )
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoCell(
    shot: Shot,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = shot.uri,
            contentDescription = shot.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else Color.Transparent
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.35f)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteFolderDialog(
    folderName: String,
    photoCountInFolder: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val hasPhotos = photoCountInFolder > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPhotos) "Delete folder and its photos?" else "Delete folder?") },
        text = {
            Text(
                if (hasPhotos) {
                    "\"$folderName\" and the ${photoCount(photoCountInFolder)} in it will be " +
                        "removed. The photos go to your phone's Recently Deleted, where you can " +
                        "get them back for 30 days."
                } else {
                    "\"$folderName\" is empty, so nothing else is affected."
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "The photos move with it, so anything already uploaded stays put.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank() && text != current,
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PhotoViewerScreen(
    shot: Shot,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = shot.uri,
            contentDescription = shot.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(vertical = 64.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
            Text(
                text = shot.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewerAction(Modifier.weight(1f), Icons.Filled.Share, "Share", onShare)
            ViewerAction(
                Modifier.weight(1f),
                Icons.AutoMirrored.Filled.DriveFileMove,
                "Move",
                onMove,
            )
            // A bin, not an X: on Android an X reads as "close", so using it for a
            // destructive action invites exactly the wrong tap.
            ViewerAction(Modifier.weight(1f), Icons.Filled.DeleteOutline, "Remove", onTrash)
        }
    }
}

@Composable
private fun ViewerAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}
