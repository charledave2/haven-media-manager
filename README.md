# Haven

Haven is a personal Android media manager for browsing and enjoying a private pCloud collection. It is built with Kotlin, Jetpack Compose, and the mpv media engine, with a library structure designed to grow beyond movies, music, and photos.

## Project status

The app includes a native Compose interface, local media library, and built-in mpv playback. pCloud access is available through WebDAV while OAuth app registration is pending. The WebDAV connection supports browsing folders and streaming common video, audio, and image formats. A browser design preview remains available in `docs/design-preview.html`.

## Direction

- Personal, calm, cinema-inspired dark interface with forest-green accents
- Home, Movies, Music, Photos, and Files sections
- Shared artwork transitions and restrained motion
- Local library plus pCloud WebDAV browsing
- Built-in mpv playback for local and remote media
- Modular categories and collections for future expansion

## Build

Open this project in Android Studio with Android SDK 36 installed, or run:

```bash
gradle assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. GitHub Actions also builds and uploads a debug APK artifact for pushes, pull requests, and manual runs.

## pCloud WebDAV

Choose the US or EU WebDAV region that matches the pCloud account, then connect with the account email and password. Credentials are encrypted on the device with Android Keystore and sent only through HTTPS. Never commit account credentials or signing keys.

pCloud Crypto folders are not accessible through WebDAV. If pCloud uses two-factor authentication, a new-login confirmation email may be required. WebDAV is intended here for personal media browsing and playback; it is not optimized for frequent transfers of very large files.

OAuth access can replace or complement WebDAV after the pCloud application registration is approved.

## License

This project is personal-use software. A license will be selected before the first public release.
