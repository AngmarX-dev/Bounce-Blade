# ⚔️ Bounce Blade

A neon-on-dark 2D physics puzzle for Android. Aim a spinning blade, launch it, and use ricochets to clear each arena.

## Included now

- Android/Kotlin project configured for JDK 17, Gradle 8.9, compile SDK 35.
- Touch aiming, launch, bouncing, slow-time control, starter arena and HUD prototype.
- Reusable starter modules for physics, normalized level data, touch gestures, progress storage, sound playback, rendering colors, and session state.
- Editable JSON level template at `app/src/main/assets/levels/level_template.json`.
- Asset folders for sprites, backgrounds, music, sound effects, and fonts.
- GitHub Actions workflow that runs unit tests, builds a debug APK, and uploads it as an artifact when the build succeeds.

## Repository map

```text
app/src/main/
  java/com/angmarx/bounceblade/
    MainActivity.kt
    core/                     shared game configuration
    game/model/               level and point models
    game/physics/             reusable blade physics
    game/input/               aim gesture state
    game/level/               JSON asset loading
    game/progression/         local progress persistence
    game/audio/               sound playback manager
    game/render/              shared color palette
    game/editor/              level draft model
    game/daily/               daily challenge data model
    game/leaderboard/         leaderboard interface
    game/session/             session state model
  assets/
    levels/                   level JSON files and template
    art/sprites/              add game sprites here
    art/backgrounds/          add arena backgrounds here
    audio/music/              add music here
    audio/sfx/                add sound effects here
    fonts/                    add licensed fonts here
  res/values/                 app strings, colors and theme
```

## Build locally

Open the project in Android Studio with JDK 17, or install Gradle 8.9 and run:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Important status

The project is a working-in-progress scaffold, not the finished game. The current `MainActivity.kt` prototype still uses hard-coded starter layouts; the JSON level repository and the separated game modules are ready to be connected during the next refactor. The online leaderboard, daily challenge service, level editor UI, 100+ level campaign, ad/reward flows, and in-app purchases are not implemented. No ad or billing SDK is included.

## Adding assets

Drop your files into the documented `app/src/main/assets/` folders. Check the asset licensing before including third-party artwork, fonts, or music. Large binary assets should be committed only when they are ready to use in the game.
