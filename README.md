# ⚔️ Bounce Blade

**Bounce Blade** is an offline 2D physics-puzzle game for Android, built with Kotlin and a custom Canvas renderer. Aim a spinning energy blade, destroy every target, hit the gold switch, and reach the portal.

## Playable features

- **20 JSON-driven campaign arenas** with local progress and locked/unlocked level selection.
- **Touch aiming and launch:** drag backward to aim and release to fire.
- **Ricochet physics:** arena-wall reflections and physical reactions to enemies and crates.
- **Objective logic:** clear enemies and crates, activate the switch, then enter the unlocked exit.
- **Hazards:** spike zones fail the current attempt.
- **Bounce budget:** levels have different limits and a retry flow.
- **Slow motion:** use a second finger while the blade is moving; the meter drains and recharges.
- **Menus:** campaign start/continue, level select, pause, retry, victory, settings, and campaign-complete screens.
- **Local settings and progress:** sound toggle, haptic toggle, and unlocked levels persist on the device.
- **Neon visuals:** blade, enemy, crate, switch, portal, particles, arena grid, and launcher icon are drawn/provided in the project; the core game does not require downloaded assets.
- **Build checks:** GitHub Actions runs unit tests and builds a debug APK, then publishes the APK as a workflow artifact if the build succeeds.

## Controls

1. Touch anywhere in the arena and drag backward to set the launch direction.
2. Release to launch. Aim to hit enemies and crates, while avoiding the spike strip.
3. Hit the gold switch. The portal opens only when all enemies and crates are cleared and the switch is active.
4. If the blade runs out of allowed bounces or hits a hazard, retry the level.
5. During flight, place a second finger on the screen to slow time.

## Project layout

```text
app/src/main/
  java/com/angmarx/bounceblade/
    MainActivity.kt               game screen, renderer, input, and campaign flow
    core/GameConfig.kt            shared game defaults
    game/model/                   normalized level data models
    game/physics/                 reusable tested wall-physics helpers
    game/input/                   aim gesture helper
    game/level/                   level asset access
    game/session/                 game state and pure gameplay rules
    game/progression/             local progress storage
    game/audio/                   SoundPool helper
    game/render/                  shared palette
    game/editor/                  level draft model
    game/daily/                   daily challenge data model
    game/leaderboard/             leaderboard contract
  assets/levels/
    level_001.json ... level_020.json
    level_template.json
  res/
    drawable/ic_launcher.xml
    values/                        theme, strings, colors
  test/                            local unit tests
```

## Build the APK

Use JDK 17 and Gradle 8.9. From the repository root:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The APK will be written to:

`app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions artifact: [open the workflow runs](https://github.com/AngmarX-dev/Bounce-Blade/actions).

## Assets and customization

Edit level JSON files in `app/src/main/assets/levels/`. Each level defines a spawn point, exit, switch, enemies, crates, hazards, and bounce budget. Coordinates are normalized from 0.0 to 1.0 so the layout scales to different screen sizes.

The playable campaign uses Canvas-drawn graphics and short device-generated tone feedback, so missing external images, music, or sound files will not prevent the game from starting.

## Not included in this offline release

Online leaderboards, cloud saves, daily-challenge services, ghost replays, a full in-game level-editor interface, advertising, and in-app purchases require additional UI/backend configuration and are not claimed as implemented.
