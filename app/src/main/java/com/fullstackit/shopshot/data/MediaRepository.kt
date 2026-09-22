package com.fullstackit.shopshot.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Every read and write goes through MediaStore rather than raw file paths, so the photos land
 * in DCIM where the Gallery and the marketplace apps (eBay, Poshmark, Mercari, Etsy) can see
 * them, and so no broad storage permission is needed to write.
 */
class MediaRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    private val collection: Uri =
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    // ---------------------------------------------------------------- reading

    suspend fun loadShots(): List<Shot> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("$ROOT_PATH%")
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"

        val out = ArrayList<Shot>()
        runCatching {
            resolver.query(collection, projection, selection, args, sort)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val folder = folderFromRelativePath(c.getString(pathCol)) ?: continue
                    out += Shot(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = c.getString(nameCol) ?: "",
                        folder = folder,
                        dateAddedSeconds = c.getLong(dateCol),
                        sizeBytes = c.getLong(sizeCol),
                    )
                }
            }
        }
        out
    }

    /**
     * Folders are derived from the photos on disk, then unioned with the names the user has
     * created in-app so a brand-new empty folder is still selectable.
     */
    fun foldersFrom(shots: List<Shot>, knownNames: Set<String>): List<ShopFolder> {
        val byFolder = shots.groupBy { it.folder }
        val names = byFolder.keys + knownNames
        return names.map { name ->
            val inside = byFolder[name].orEmpty()
            ShopFolder(
                name = name,
                count = inside.size,
                coverUri = inside.firstOrNull()?.uri,
                lastAddedSeconds = inside.maxOfOrNull { it.dateAddedSeconds } ?: 0L,
            )
        }.sortedWith(
            // Folders used most recently float to the top; untouched ones sort alphabetically.
            compareByDescending<ShopFolder> { it.lastAddedSeconds }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
        )
    }

    // ---------------------------------------------------------------- writing

    /** ContentValues for a fresh capture; handed straight to CameraX. */
    fun newImageValues(folder: String, existingCount: Int): ContentValues {
        val safe = sanitizeFolderName(folder)
        val index = (existingCount + 1).coerceAtLeast(1)
        val name = "%s_%03d.jpg".format(Locale.US, safe.replace(' ', '_'), index)
        return ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePathFor(safe))
        }
    }

    fun imageCollection(): Uri = collection

    /** Moves photos into [target] by rewriting RELATIVE_PATH - no copy, no re-encode. */
    suspend fun moveToFolder(uris: List<Uri>, target: String): MediaResult =
        withContext(Dispatchers.IO) {
            val safe = sanitizeFolderName(target)
            val blocked = ArrayList<Uri>()
            var moved = 0
            var failed = 0
            for (uri in uris) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePathFor(safe))
                }
                try {
                    resolver.update(uri, values, null, null)
                    moved++
                } catch (e: SecurityException) {
                    blocked += uri
                } catch (e: Exception) {
                    // Usually a display-name collision in the destination; retry once renamed.
                    val renamed = ContentValues(values).apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, uniqueName(uri))
                    }
                    try {
                        resolver.update(uri, renamed, null, null)
                        moved++
                    } catch (e2: SecurityException) {
                        blocked += uri
                    } catch (e2: Exception) {
                        failed++
                    }
                }
            }
            if (blocked.isEmpty()) {
                MediaResult.Done(moved, failed)
            } else {
                MediaResult.NeedsConsent(
                    intentSender = MediaStore.createWriteRequest(resolver, blocked).intentSender,
                    alreadyAffected = moved,
                    retryUris = blocked,
                )
            }
        }

    /**
     * Moves to the system trash rather than deleting outright, so a mis-tap is recoverable
     * from the Gallery's Recently Deleted for 30 days. Always needs one system confirmation.
     */
    suspend fun trash(uris: List<Uri>): MediaResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext MediaResult.Done(0)
        MediaResult.NeedsConsent(
            intentSender = MediaStore.createTrashRequest(resolver, uris, true).intentSender,
            alreadyAffected = 0,
            retryUris = emptyList(),
        )
    }

    /** Copies photos picked from anywhere on the device into [target]. */
    suspend fun importInto(sources: List<Uri>, target: String, startIndex: Int): Int =
        withContext(Dispatchers.IO) {
            val safe = sanitizeFolderName(target)
            var copied = 0
            sources.forEachIndexed { i, source ->
                val values = newImageValues(safe, startIndex + i)
                val dest = runCatching { resolver.insert(collection, values) }.getOrNull()
                    ?: return@forEachIndexed
                val ok = runCatching {
                    resolver.openInputStream(source)?.use { input ->
                        resolver.openOutputStream(dest)?.use { output -> input.copyTo(output) }
                            ?: throw IllegalStateException("no output stream")
                    } ?: throw IllegalStateException("no input stream")
                }.isSuccess
                if (ok) copied++ else runCatching { resolver.delete(dest, null, null) }
            }
            copied
        }

    suspend fun renameFolder(from: String, to: String, shots: List<Shot>): MediaResult {
        val uris = shots.filter { it.folder == from }.map { it.uri }
        if (uris.isEmpty()) return MediaResult.Done(0)
        return moveToFolder(uris, to)
    }

    private fun uniqueName(uri: Uri): String {
        val base = runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
                null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: "photo.jpg"
        val stem = base.substringBeforeLast('.', base)
        val ext = base.substringAfterLast('.', "jpg")
        return "${stem}_${System.currentTimeMillis() % 100000}.$ext"
    }

    companion object {
        const val ROOT_DIR = "ShopShot"
        val ROOT_PATH = "${Environment.DIRECTORY_DCIM}/$ROOT_DIR/"

        fun relativePathFor(folder: String): String = "$ROOT_PATH${sanitizeFolderName(folder)}/"

        /** "DCIM/ShopShot/Blue Vase/" -> "Blue Vase"; anything outside the root -> null. */
        fun folderFromRelativePath(path: String?): String? {
            if (path.isNullOrBlank()) return null
            if (!path.startsWith(ROOT_PATH)) return null
            val tail = path.removePrefix(ROOT_PATH).trim('/')
            if (tail.isEmpty()) return null
            return tail.substringBefore('/')
        }

        /** MediaStore rejects these outright, and they break the marketplace uploaders too. */
        fun sanitizeFolderName(raw: String): String {
            val cleaned = raw.trim()
                .map { if (it.isISOControl() || it in ILLEGAL_CHARS) ' ' else it }
                .joinToString("")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trimEnd('.')
            return if (cleaned.isEmpty()) Prefs.DEFAULT_FOLDER else cleaned.take(60)
        }

        private val ILLEGAL_CHARS = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
    }
}
