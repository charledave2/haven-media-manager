# Haven

Haven is a personal Android media manager concept for browsing and enjoying a private pCloud collection. The app is being built as a native Kotlin and Jetpack Compose project, with a modular library designed to grow beyond movies, music, and photos.

## Project status

The first Android foundation is in place: a native Compose shell, premium dark visual direction, bottom navigation, sample media collections, screen transitions, and a mini-player. All content currently uses sample metadata and remote placeholder artwork. pCloud sign-in and live media access are planned next and are not implemented yet. A browser design preview is available in `docs/design-preview.html`.

## Direction

- Personal, calm, cinema-inspired dark interface with forest-green accents
- Home, Movies, Music, Photos, and Files sections
- Shared artwork transitions and restrained motion
- pCloud OAuth integration using the official pCloud Android SDK after app registration
- Expandable media categories and collections

## Build

Open this project in Android Studio with Android SDK 36 installed, or run:

```bash
gradle assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. GitHub Actions also builds and uploads a debug APK artifact for pushes, pull requests, and manual runs.

## pCloud setup

Before live account access can be enabled, register a pCloud API application, configure its OAuth redirect URI for the Haven Android package, and identify the account's pCloud region (US or EU). Never commit OAuth tokens, signing keys, or credentials.

## License

This project is personal-use software. A license will be selected before the first public release.
