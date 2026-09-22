package com.fullstackit.shopshot.data

import android.content.IntentSender
import android.net.Uri

/** A single photo living somewhere under DCIM/ShopShot/. */
data class Shot(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val folder: String,
    val dateAddedSeconds: Long,
    val sizeBytes: Long,
)

/** One destination folder, e.g. DCIM/ShopShot/Blue Vase. */
data class ShopFolder(
    val name: String,
    val count: Int,
    val coverUri: Uri?,
    val lastAddedSeconds: Long,
)

/**
 * Result of a MediaStore write. Rows this app created can be changed silently; rows another
 * app owns (stock camera, a file manager) need a one-tap system consent dialog first, which
 * is what [NeedsConsent] carries back to the UI.
 */
sealed interface MediaResult {
    data class Done(val affected: Int, val failed: Int = 0) : MediaResult
    data class NeedsConsent(
        val intentSender: IntentSender,
        val alreadyAffected: Int,
        val retryUris: List<Uri>,
    ) : MediaResult
}
