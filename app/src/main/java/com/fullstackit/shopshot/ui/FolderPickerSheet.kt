package com.fullstackit.shopshot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fullstackit.shopshot.data.ShopFolder
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    state: UiState,
    title: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
    onToggleAsk: (Boolean) -> Unit = {},
    // "Ask before every shot" is a camera setting. It has no meaning when this sheet is
    // picking a move or import destination, so those callers hide it.
    showAskToggle: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val matches = remember(state.folders, query) {
        if (query.isBlank()) {
            state.folders
        } else {
            state.folders.filter {
                it.name.contains(query.trim(), ignoreCase = true)
            }
        }
    }
    val trimmed = query.trim()
    // Typing a name that does not exist yet turns the search box into a create action,
    // so making a folder for a new listing is one flow, not two.
    val canCreate = trimmed.isNotEmpty() &&
        state.folders.none { it.name.equals(trimmed, ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {

            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(4.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                placeholder = { Text("Search or type a new folder name") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (canCreate) {
                        IconButton(onClick = { onCreate(trimmed) }) {
                            Icon(
                                Icons.Filled.CreateNewFolder,
                                contentDescription = "Create folder",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        when {
                            canCreate -> onCreate(trimmed)
                            matches.isNotEmpty() -> onPick(matches.first().name)
                        }
                    }
                ),
                shape = RoundedCornerShape(14.dp),
            )

            if (canCreate) {
                CreateRow(name = trimmed, onClick = { onCreate(trimmed) })
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(matches, key = { it.name }) { folder ->
                    FolderRow(
                        folder = folder,
                        selected = folder.name == state.currentFolder,
                        onClick = { onPick(folder.name) },
                    )
                }
            }

            if (showAskToggle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onToggleAsk(!state.askEveryShot) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ask before every shot", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Off means keep shooting into the same folder until you change it",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.askEveryShot, onCheckedChange = onToggleAsk)
                }
            }
        }
    }
}

@Composable
private fun CreateRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.CreateNewFolder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Create \"$name\"",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FolderRow(folder: ShopFolder, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (folder.coverUri != null) {
                AsyncImage(
                    model = folder.coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = countLabel(folder.count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Current folder",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** "1 photo" / "7 photos" - used in titles, where "photo(s)" reads like a form letter. */
internal fun photoCount(count: Int): String =
    if (count == 1) "1 photo" else String.format(Locale.getDefault(), "%d photos", count)

internal fun countLabel(count: Int): String = when (count) {
    0 -> "Empty"
    1 -> "1 photo"
    else -> String.format(Locale.getDefault(), "%d photos", count)
}
