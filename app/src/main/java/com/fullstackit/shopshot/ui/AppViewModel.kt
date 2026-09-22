package com.fullstackit.shopshot.ui

import android.app.Application
import android.content.ContentValues
import android.content.IntentSender
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fullstackit.shopshot.data.MediaRepository
import com.fullstackit.shopshot.data.MediaResult
import com.fullstackit.shopshot.data.Prefs
import com.fullstackit.shopshot.data.ShopFolder
import com.fullstackit.shopshot.data.Shot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class UiState(
    val shots: List<Shot> = emptyList(),
    val folders: List<ShopFolder> = emptyList(),
    val currentFolder: String = Prefs.DEFAULT_FOLDER,
    val askEveryShot: Boolean = false,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val useFrontCamera: Boolean = false,
    val sessionShots: List<Shot> = emptyList(),
    val loading: Boolean = true,
) {
    val currentFolderCount: Int get() = shots.count { it.folder == currentFolder }
}

/** One-shot things the UI has to act on rather than render. */
sealed interface UiEvent {
    data class Toast(val message: String) : UiEvent
    data class Consent(val intentSender: IntentSender) : UiEvent
}

/** What we are waiting on the system consent dialog for. */
private sealed interface Pending {
    data class Move(val uris: List<Uri>, val target: String) : Pending
    data object Trash : Pending
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository(app)
    private val prefs = Prefs(app)

    private val _state = MutableStateFlow(
        UiState(
            currentFolder = prefs.currentFolder,
            askEveryShot = prefs.askEveryShot,
            flashMode = prefs.flashMode,
            useFrontCamera = prefs.useFrontCamera,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var pending: Pending? = null

    init {
        // The default folder should always exist so the very first shot has somewhere to go.
        prefs.rememberFolder(prefs.currentFolder)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val shots = repo.loadShots()
            val folders = repo.foldersFrom(shots, prefs.knownFolders)
            _state.value = _state.value.copy(
                shots = shots,
                folders = folders,
                loading = false,
                // Re-resolve session thumbnails against the fresh list so a moved or trashed
                // photo drops out of the filmstrip instead of showing a dead URI.
                sessionShots = _state.value.sessionShots.mapNotNull { old ->
                    shots.firstOrNull { it.id == old.id }
                },
            )
        }
    }

    // ------------------------------------------------------------ folder state

    fun selectFolder(name: String) {
        val safe = MediaRepository.sanitizeFolderName(name)
        prefs.currentFolder = safe
        prefs.rememberFolder(safe)
        _state.value = _state.value.copy(currentFolder = safe)
        refresh()
    }

    fun createFolder(name: String): String {
        val safe = MediaRepository.sanitizeFolderName(name)
        prefs.rememberFolder(safe)
        selectFolder(safe)
        return safe
    }

    fun setAskEveryShot(value: Boolean) {
        prefs.askEveryShot = value
        _state.value = _state.value.copy(askEveryShot = value)
    }

    fun cycleFlash() {
        val next = when (_state.value.flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        prefs.flashMode = next
        _state.value = _state.value.copy(flashMode = next)
    }

    fun toggleLens() {
        val next = !_state.value.useFrontCamera
        prefs.useFrontCamera = next
        _state.value = _state.value.copy(useFrontCamera = next)
    }

    // ---------------------------------------------------------------- capture

    fun imageValuesForCurrentFolder(folder: String = _state.value.currentFolder): ContentValues =
        repo.newImageValues(folder, _state.value.shots.count { it.folder == folder })

    fun imageCollection(): Uri = repo.imageCollection()

    /** Called with the URI CameraX just wrote, so the filmstrip updates without a full reload. */
    fun onCaptured(uri: Uri?, folder: String) {
        viewModelScope.launch {
            val shots = repo.loadShots()
            val folders = repo.foldersFrom(shots, prefs.knownFolders)
            val fresh = uri?.let { u -> shots.firstOrNull { it.uri == u } }
                ?: shots.firstOrNull { it.folder == folder }
            _state.value = _state.value.copy(
                shots = shots,
                folders = folders,
                sessionShots = listOfNotNull(fresh) +
                    _state.value.sessionShots.filter { it.id != fresh?.id },
            )
        }
    }

    fun clearSession() {
        _state.value = _state.value.copy(sessionShots = emptyList())
    }

    // ------------------------------------------------------------- bulk edits

    fun move(uris: List<Uri>, target: String) {
        if (uris.isEmpty()) return
        val safe = MediaRepository.sanitizeFolderName(target)
        viewModelScope.launch {
            prefs.rememberFolder(safe)
            runMove(uris, safe)
        }
    }

    private suspend fun runMove(uris: List<Uri>, target: String) {
        when (val result = repo.moveToFolder(uris, target)) {
            is MediaResult.Done -> {
                pending = null
                val suffix = if (result.failed > 0) " (${result.failed} failed)" else ""
                _events.send(
                    UiEvent.Toast(
                        "Moved ${result.affected} photo${plural(result.affected)} to $target$suffix"
                    )
                )
                refresh()
            }
            is MediaResult.NeedsConsent -> {
                // Remember exactly which photos still need doing and where they were headed,
                // so granting consent resumes the same move rather than guessing a target.
                pending = Pending.Move(result.retryUris, target)
                _events.send(UiEvent.Consent(result.intentSender))
            }
        }
    }

    fun trash(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            when (val result = repo.trash(uris)) {
                is MediaResult.NeedsConsent -> {
                    pending = Pending.Trash
                    _events.send(UiEvent.Consent(result.intentSender))
                }
                is MediaResult.Done -> refresh()
            }
        }
    }

    fun importInto(sources: List<Uri>, target: String) {
        if (sources.isEmpty()) return
        viewModelScope.launch {
            val safe = MediaRepository.sanitizeFolderName(target)
            prefs.rememberFolder(safe)
            val startIndex = _state.value.shots.count { it.folder == safe }
            val copied = repo.importInto(sources, safe, startIndex)
            _events.send(UiEvent.Toast("Added $copied photo${plural(copied)} to $safe"))
            refresh()
        }
    }

    fun renameFolder(from: String, to: String) {
        val safe = MediaRepository.sanitizeFolderName(to)
        if (safe == from) return
        viewModelScope.launch {
            val uris = _state.value.shots.filter { it.folder == from }.map { it.uri }
            prefs.forgetFolder(from)
            prefs.rememberFolder(safe)
            if (_state.value.currentFolder == from) selectFolder(safe)
            if (uris.isEmpty()) {
                _events.send(UiEvent.Toast("Renamed to $safe"))
                refresh()
            } else {
                runMove(uris, safe)
            }
        }
    }

    /** Drops an empty folder from the picker. Folders with photos are left alone. */
    fun forgetEmptyFolder(name: String) {
        if (_state.value.shots.any { it.folder == name }) return
        prefs.forgetFolder(name)
        if (_state.value.currentFolder == name) selectFolder(Prefs.DEFAULT_FOLDER)
        refresh()
    }

    /** Result of the system consent dialog the UI launched for us. */
    fun onConsentResult(granted: Boolean) {
        val p = pending
        pending = null
        if (!granted) {
            viewModelScope.launch { _events.send(UiEvent.Toast("Cancelled")) }
            refresh()
            return
        }
        when (p) {
            // The system performed the trash itself once consent was given.
            Pending.Trash, null -> refresh()
            is Pending.Move -> viewModelScope.launch { runMove(p.uris, p.target) }
        }
    }

    /** Surfaces a capture or storage failure to the user instead of failing silently. */
    fun reportError(message: String) {
        viewModelScope.launch { _events.send(UiEvent.Toast(message)) }
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"
}
