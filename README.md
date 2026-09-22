# ShopShot

A camera app for selling online. You pick the folder first, then every photo you take lands in
it — instead of 200 shots from six different listings piling up in one camera roll.

## What it does

- **Pick a destination, then shoot.** The folder name sits across the top of the camera. Tap it,
  choose or create a folder, and every shot goes there until you change it.
- **Type to create.** In the folder picker, typing a name that doesn't exist turns the search box
  into a "Create" button. Making a folder for a new listing is one action.
- **Ask every shot.** Optional toggle, for when you're photographing a mixed pile of items and
  want to be asked each time.
- **Folders view.** Cover photo and count per listing. Rename a folder and the photos move with it.
- **Fix mistakes in bulk.** Long-press any photo to start selecting, then move, share or remove
  the whole selection at once.
- **Pull in existing photos.** "Add existing photos" copies shots from the normal camera roll
  into a listing folder.
- **Share straight to a marketplace.** Select the photos for a listing and hit Share.

## Where the photos go

Everything lands in `DCIM/ShopShot/<Folder Name>/` in normal phone storage.

That location matters: it's in `DCIM`, so the Gallery, Google Photos, and the upload pickers in
eBay, Poshmark, Mercari and Etsy all see these as ordinary albums. Nothing is locked inside the
app, and uninstalling ShopShot leaves every photo where it is.

Files are named after the folder with a running number — `Blue_Vase_001.jpg`, `Blue_Vase_002.jpg`
— so they stay in shooting order when uploaded.

## Getting the APK

Pushing to `main` builds it. Open the repo's **Actions** tab → newest **Build APK** run →
download the `ShopShot-debug-apk` artifact → unzip → transfer `app-debug.apk` to the phone and
open it. Android will ask permission to install from this source the first time.

To build locally instead, with JDK 17 and the Android SDK installed:

```
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # smaller, and what you'd actually put on a phone
./gradlew installDebug        # straight onto a USB-connected phone
```

`local.properties` (gitignored) must point at the SDK, using forward slashes:

```
sdk.dir=F:/Android/Sdk
```

## Requirements

- **Android 11 (API 30) or newer.** The app relies on MediaStore being able to move a photo
  between folders by rewriting its path, which landed in Android 11. To support Android 10,
  change `minSdk` in `app/build.gradle.kts` and add a legacy file-path fallback for moves.
- Camera permission — required, it's the whole app.
- Photo access permission — optional. Without it the app still sees every photo *it* took; with
  it, it can also manage shots that another app dropped into the shop folders.

## Permission prompts you'll see

Moving or deleting a photo that ShopShot didn't take shows a one-tap system confirmation. That's
Android's rule for touching another app's media, not something the app can skip. Removing a photo
sends it to the system trash, so it's recoverable from the Gallery's "Recently deleted" for 30 days.

## Project layout

```
app/src/main/java/com/fullstackit/shopshot/
  data/
    MediaRepository.kt   all MediaStore reads and writes
    Prefs.kt             selected folder, known folder names, camera settings
    Models.kt
  ui/
    ShopShotRoot.kt      navigation, permissions, system consent dialogs
    CameraScreen.kt      preview, shutter, destination chip, session filmstrip
    FolderPickerSheet.kt search-or-create bottom sheet
    FoldersScreen.kt     folder grid, import existing photos
    FolderDetailScreen.kt photo grid, multi-select, move/share/remove
```

## Signing

Release builds are currently signed with the debug key so `assembleRelease` produces something
installable. Swap in a real keystore in `app/build.gradle.kts` before distributing this anywhere
beyond your own phones.
