package com.fullstackit.shopshot.data

import android.content.Context

/**
 * Small settings store. Folder names are kept here as well as in MediaStore so that a folder
 * created before its first photo still shows up in the picker.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("shopshot", Context.MODE_PRIVATE)

    var knownFolders: Set<String>
        get() = sp.getStringSet(KEY_FOLDERS, emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet(KEY_FOLDERS, value).apply()

    var currentFolder: String
        get() = sp.getString(KEY_CURRENT, DEFAULT_FOLDER)!!
        set(value) = sp.edit().putString(KEY_CURRENT, value).apply()

    var askEveryShot: Boolean
        get() = sp.getBoolean(KEY_ASK, false)
        set(value) = sp.edit().putBoolean(KEY_ASK, value).apply()

    /** ImageCapture.FLASH_MODE_* */
    var flashMode: Int
        get() = sp.getInt(KEY_FLASH, 2) // FLASH_MODE_OFF
        set(value) = sp.edit().putInt(KEY_FLASH, value).apply()

    var useFrontCamera: Boolean
        get() = sp.getBoolean(KEY_FRONT, false)
        set(value) = sp.edit().putBoolean(KEY_FRONT, value).apply()

    fun rememberFolder(name: String) {
        knownFolders = knownFolders + name
    }

    fun forgetFolder(name: String) {
        knownFolders = knownFolders - name
    }

    companion object {
        const val DEFAULT_FOLDER = "Unsorted"
        private const val KEY_FOLDERS = "known_folders"
        private const val KEY_CURRENT = "current_folder"
        private const val KEY_ASK = "ask_every_shot"
        private const val KEY_FLASH = "flash_mode"
        private const val KEY_FRONT = "use_front_camera"
    }
}
