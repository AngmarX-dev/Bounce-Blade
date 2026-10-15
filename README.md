# ⚔️ Bounce Blade

**Bounce Blade** is an offline Android physics-puzzle game built with Kotlin and a custom Canvas renderer. Aim a spinning energy blade, use ricochets to clear enemies and crates, activate the switch, and reach the exit.

## Gameplay features

- **20 editable campaign levels** loaded from JSON, with unlock progression and local save data.
- **Trajectory preview with ricochet prediction** while aiming; forecast segments highlight likely target, crate, and hazard collisions.
- **Blade trail and hit particles** for clearer feedback on each impact.
- **Three-star difficulty selection:** Easy gives 50% more bounces, Normal uses the level's default budget, and Hard gives 70% of that budget.
- **Three-star level cards:** all three stars stay visible; earned stars are filled in and saved independently for each difficulty.
- **Three-star completion ratings** based on remaining bounce budget and launch efficiency; best rating is saved for each level and difficulty.
- **Help screen** with controls, objectives and hazard tips.
- **Three selectable color themes** (Cyan, Green, Violet), saved between launches.
- **Animated moving hazards** in addition to spike strips and laser beams.
- **Real objective gating:** clear every enemy and crate, activate the gold switch, then reach the portal.
- **Bounce budget, retry flow, pause menu, victory/failure screens, and level selection.**
- **Two-finger slow motion** with a rechargeable meter.
- **Persistent settings** for sound, vibration, color theme, selected difficulty, campaign progress and best stars.
- **No external art or audio is required to start:** the game draws its core art and provides device-generated sound feedback.
- **GitHub Actions** validates level files, runs unit tests, builds the debug APK, and uploads it as an artifact.

## Controls

1. In the arena, drag backward to aim. The preview line shows predicted ricochets and likely impacts.
2. Release to launch the blade.
3. Use wall and object rebounds to destroy enemies and crates.
4. Hit the gold switch after clearing the required targets. The exit only accepts the blade when all objectives are satisfied.
5. Keep an eye on moving lasers and spike strips. A hazard hit restarts the level attempt.
6. Place a second finger on the screen during flight to slow time.

## Build

Requires JDK 17. In Android Studio, open the repository and sync Gradle; alternatively, run from the repository root with Gradle 8.9:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

The [GitHub Actions page](https://github.com/AngmarX-dev/Bounce-Blade/actions) contains the latest build runs and the downloadable debug APK artifact.

## Editing levels

Edit `app/src/main/assets/levels/level_001.json` through `level_020.json`. Coordinates are normalized from 0.0 to 1.0. Supported hazard types are `spikes`, `laser`, and `moving_hazard`. Keep each file's numeric `id` consistent with its three-digit filename.

## Project layout

```text
app/src/main/
  java/com/angmarx/bounceblade/
    MainActivity.kt                 gameplay, rendering, touch, campaign flow
    core/GameConfig.kt              shared game defaults
    game/model/                     normalized level models
    game/physics/                   reusable wall physics
    game/input/                     aim gesture helper
    game/level/                     JSON level repository
    game/session/                   pure game rules and session state
    game/progression/               local progress storage
    game/audio/                     audio helper
    game/render/                    shared palette
    game/editor/                    future level-editor data model
    game/daily/                     daily challenge data model
    game/leaderboard/               online leaderboard contract
  assets/levels/                    20 campaign files plus template
  res/                              Android theme, colors and launcher icon
  test/                             unit tests
```

## Not included

This is an offline playable campaign. Online leaderboards, cloud saves, an in-game level-editor UI, ghost replays, daily-challenge servers, advertising, and in-app purchases still require additional implementation and backend configuration.
