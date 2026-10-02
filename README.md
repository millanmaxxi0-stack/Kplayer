# KPlayer

Kotlin + Jetpack Compose + Media3 media player.

## Build with GitHub (no PC needed)
1. Create a new repo and upload ALL files (including the hidden `.github` folder).
2. Push to `main` (or `master`) -> Actions tab -> "Build APK" runs automatically.
3. When it finishes, download `KPlayer-debug` or `KPlayer-release` from Artifacts.
4. Optional: create a tag like `v1.0` to publish the APK as a GitHub Release.

The workflow generates the Gradle wrapper, runs `chmod +x gradlew`, accepts SDK licenses and builds.
