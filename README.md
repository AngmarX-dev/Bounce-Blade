# Bounce Blade — Compose Arcade branch

This branch contains a separate Jetpack Compose version of Bounce Blade, based on the Kotlin game source supplied for this variant. It does **not** replace the main branch; the main branch keeps the original Canvas/Fhysics implementation.

## Game

- Landscape, immersive fullscreen Android game.
- Dark-blue and cyan neon grid with an animated spinning blade and particle trails.
- The blade launches automatically toward moving enemies and returns toward the player side.
- Drag anywhere vertically to move the shield and catch the returning blade.
- Catching earns points; missed catches consume a life. Lose all three lives and the stage ends.
- Enemies move, have stage-scaled health, and take damage when the blade hits. Clear all targets to win a stage.
- Stage selection, pause/resume, retry, victory/defeat overlays, and short procedural sound effects.
- Jetpack Compose Canvas rendering, Compose UI overlays, Kotlin frame-clock updates.

This is an alternate arcade design and its mechanics/UI differ from the campaign, shop, custom-art and Fhysics branch on `main`.

## Build

Requirements: JDK 17, Android SDK (API 35), and Gradle 8.9.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions validates the game launcher, runs unit tests, and builds the APK.

## Key files

- `app/src/main/java/com/angmarx/bounceblade/ComposeArcadeActivity.kt` — the alternate Compose game.
- `app/src/main/java/com/angmarx/bounceblade/MainActivity.kt` — the original Canvas activity retained for comparison on this branch; it is not the launcher here.
- `app/build.gradle.kts` — Compose dependencies and compiler setup.
