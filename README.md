# ⚔️ Bounce Blade

**Bounce Blade** is an offline Android arcade game built with Kotlin and a custom Canvas renderer. A spinning blade launches automatically from the left, hits enemies and ricochets back. You move a glowing shield along the left wall to catch every return.

## Gameplay features

- **50 stages across five worlds:** Neon Grid, Crimson Circuit, Azure Vault, Magenta Maze and Golden Core, with linear unlocking and local saves.
- **Automatic blade launch:** each stage begins with the blade flying into the arena; enemy impacts reverse its horizontal direction, sending it back toward the player.
- **Player-controlled left shield:** touch and drag vertically anywhere on the screen to move the shield up or down.
- **Landscape orientation:** the app opens and stays in horizontal fullscreen mode, giving the blade arena more width for tracking enemies and catching returns.
- **Instant-loss rule:** if the returning blade passes the shield at the left edge, the run ends immediately.
- **Enemy waves:** every stage creates a growing formation of enemies, with tougher enemy types and multi-hit targets in later stages.
- **Score, impact effects and catch streaks:** hits, catches and ricochets use the included visual and audio feedback.
- **Three difficulty levels:** Easy gives the shield a larger catch area, Normal is balanced, and Hard uses a smaller shield with a faster blade.
- **Bundled game art and UI:** menu/gameplay backgrounds, game logo, blade sprites, drone/turret/slime sprites, buttons, banners, and impact effects.
- **Tightly framed game art:** transparent outer margins are cropped when PNGs load, so buttons, icons, blades, and enemies use their visible bounds without re-encoding or inflating the original image files.
- **Soundtrack and SFX:** looping menu/gameplay/victory/game-over music plus blade launch, ricochet, enemy-hit and UI sounds. The Sound setting controls music and effects.
- **Three selectable color themes** (Cyan, Green, Violet), plus saved campaign progress, score stars, sound and vibration settings.
- **Pause, retry, stage selection and victory/failure screens.**
- **GitHub Actions** validates the assets and 50 level files, runs unit tests, builds the debug APK, and uploads it as an artifact.

## Controls

0. Play in horizontal (landscape) mode; rotate your device sideways if auto-rotate is disabled.
1. The blade launches automatically when a stage starts.
2. Touch anywhere and drag **up or down** to move the glowing shield on the left wall.
3. When the blade hits an enemy, it bounces back. Catch it by moving the shield into its path.
4. If the blade slips past the shield and exits on the left, you lose immediately.
5. Clear every enemy to finish the stage; use **Ⅱ** in the top-right corner to pause.

## Build

Requires JDK 17. In Android Studio, open the repository and sync Gradle; alternatively, run from the repository root with Gradle 8.9:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

The [GitHub Actions page](https://github.com/AngmarX-dev/Bounce-Blade/actions) contains the latest build runs and the downloadable debug APK artifact.

## Art and audio assets

The build includes the original repository-level `Images/` and `SoundTrack/` folders through the Android asset source sets. Do not rename these folders or the filenames referenced by `MainActivity.kt`. The provided `Images/App Icon & Logo/App Icon.png` is copied to generated `mipmap-nodpi/ic_launcher.png` during the Gradle build and is used as the installed app icon.

## Editing levels

Edit `app/src/main/assets/levels/level_001.json` through `level_050.json`. Coordinates are normalized from 0.0 to 1.0. Supported hazards are `spikes`, `laser`, `moving_hazard`, `sticky_wall`, `teleporter`, and `power_up`. Each level declares its world and target/switch requirements; teleporters need a pair.

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
  assets/levels/                    50 campaign files plus template
  res/                              Android theme, colors and launcher icon
  test/                             unit tests
```

## Not included

This is an offline playable campaign. Online leaderboards, cloud saves, an in-game level-editor UI, ghost replays, daily-challenge servers, advertising, and in-app purchases still require additional implementation and backend configuration.
