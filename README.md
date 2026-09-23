# ShopShot

**A camera app for people who sell things online.** You pick which folder a photo goes in
*before* you take it — so your listings sort themselves instead of piling into one giant camera
roll.

Free, no ads, no account, and it never sends your photos anywhere.

---

## The problem it solves

You photograph a jacket, a lamp, and three pairs of shoes. Forty pictures later they're all
jumbled together in one camera roll, and now you're squinting at thumbnails trying to remember
which shoes went with which listing.

ShopShot fixes that by asking one question first: **where should this go?**

---

## How it works

1. **Tap the folder name** across the top of the camera.
2. **Pick a folder, or type a new name** — for example `Blue Vase`. Typing a name that doesn't
   exist turns the box into a "Create" button, so making a folder for a new listing is one tap.
3. **Shoot as many photos as you want.** Every one lands in that folder until you change it.

That's the whole app. Everything else is there for when you need to tidy up afterwards.

There's also an **Ask before every shot** switch, for when you're working through a mixed pile
of items and want to be asked each time.

---

## Installing it

ShopShot isn't on the Google Play Store, so you install it directly. It takes about a minute.

1. Go to the [**Releases page**](../../releases/latest) and download the **`.apk`** file onto
   your phone.
2. Open it. Your phone will say something like *"For your security, your phone isn't allowed to
   install unknown apps from this source."* Tap **Settings**, turn the switch on, then press
   back and tap the file again.
3. You may then see a **Play Protect** warning saying the app wasn't scanned or is unrecognised.
   Tap **More details → Install anyway**.

**Is that warning something to worry about?** It's Google saying "I don't recognise whoever made
this," which is true of every app not distributed through the Play Store. It is not a virus
warning. If that makes you uncomfortable, that's a completely reasonable instinct — the source
code is all here for anyone to inspect, and you can build it yourself.

**You'll need Android 11 or newer.** Check under Settings → About phone → Android version.

---

## Where your photos actually go

Into your phone's normal photo storage, at:

```
DCIM/ShopShot/<your folder name>/
```

This matters more than it sounds:

- **They show up in your Gallery** as normal albums, same as any other photo.
- **The selling apps can see them.** When eBay, Poshmark, Mercari or Etsy ask you to pick
  photos, your ShopShot folders are right there.
- **They're not trapped in the app.** If you uninstall ShopShot tomorrow, every photo stays
  exactly where it is.

Photos are named after the folder with a running number — `Blue_Vase_001.jpg`,
`Blue_Vase_002.jpg` — so they stay in the order you shot them when you upload.

---

## The rest of it

**Folders screen** (the icon in the top right) shows every listing with its cover photo and a
count.

**Fix mistakes in bulk.** Open a folder and long-press any photo to start selecting. Then move
the whole selection to another folder, share them, or remove them.

**Rename a folder** and the photos move with it.

**Delete a folder** from the ⋮ menu inside it. If it still has photos, they go to your phone's
Recently Deleted, where you can get them back for 30 days.

**Add existing photos** pulls pictures you already took with your normal camera into a listing
folder.

**Share to a marketplace.** Select the photos for a listing, hit Share, and pick the app.

---

## Your privacy

ShopShot has no internet connection of any kind. There is no account, no sign-up, no analytics,
no ads, and no tracking. Nothing you photograph leaves your phone.

The app asks for **one** permission: the camera. That's it. It doesn't ask for access to your
photo library, because it doesn't need it — it can already see the photos it took, and pulling
in older pictures goes through Android's own photo picker.

---

## If something goes wrong

**The camera is black.** Tap "Try again" if you see it. If the screen is black with no message,
close the app fully and reopen it, then please [open an issue](../../issues) — that shouldn't
happen.

**A photo went into the wrong folder.** Open the folder, long-press the photo, tap Move.

**"App not installed" when updating.** This happens if you previously installed a different
build. Uninstall ShopShot first, then install the new file. **Your photos are safe** — they live
outside the app and aren't touched by uninstalling.

**Android asks permission to move or delete a photo.** That's Android's rule for touching
pictures another app took, not something ShopShot can skip. Tap Allow.

---

## For developers

Kotlin, Jetpack Compose and CameraX. Minimum Android 11 (API 30), targets API 35.

```bash
git clone https://github.com/Revg1794/ShopShot.git
cd ShopShot
./gradlew assembleDebug
```

You'll need JDK 17 and the Android SDK. Create `local.properties` pointing at your SDK, using
forward slashes:

```properties
sdk.dir=C:/Users/you/AppData/Local/Android/Sdk
```

Release builds are signed from a `keystore.properties` file (gitignored) or `SHOPSHOT_*`
environment variables. Without either, the release build falls back to the debug key so a fresh
clone still builds — it just produces an APK that can't update a properly signed install.

```
app/src/main/java/com/fullstackit/shopshot/
  data/
    MediaRepository.kt      every MediaStore read and write
    Prefs.kt                selected folder, known folder names, camera settings
    Models.kt
  ui/
    ShopShotRoot.kt         navigation, permissions, system consent dialogs
    CameraScreen.kt         preview, shutter, destination chip, session filmstrip
    FolderPickerSheet.kt    search-or-create bottom sheet
    FoldersScreen.kt        folder grid, importing existing photos
    FolderDetailScreen.kt   photo grid, multi-select, move / share / remove
```

Two design decisions worth knowing before changing anything:

- **Everything goes through MediaStore, not file paths.** That's what keeps the photos in `DCIM`
  where the Gallery and the marketplace apps can see them, and what avoids needing a broad
  storage permission.
- **Android 11 is the floor** because moving a photo between folders by rewriting its
  `RELATIVE_PATH` landed in Android 11. Supporting Android 10 means adding a legacy file-path
  fallback for moves.

---

## License

MIT — see [LICENSE](LICENSE). Do what you like with it.
