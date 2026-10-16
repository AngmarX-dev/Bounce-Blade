# ⚔️ Bounce Blade

**Bounce Blade** is an offline Android arcade game built with Kotlin and a custom Canvas renderer. A spinning blade launches automatically from the left, hits enemies and ricochets back. You move a glowing shield along the left wall to catch every return.

> **Platform:** Android (offline)  
> **Language:** Kotlin  
> **Rendering:** Custom Canvas  
> **Orientation:** Landscape fullscreen  
> **Build:** Gradle 8.9 / JDK 17

---

## Table of Contents

- [Gameplay Features](#gameplay-features)
- [Worlds & Stages](#worlds--stages)
- [Enemies & Hazards](#enemies--hazards)
- [Power-ups & Pickups](#power-ups--pickups)
- [Progression & Customization](#progression--customization)
- [Controls](#controls)
- [Difficulty Levels](#difficulty-levels)
- [Visuals & Audio](#visuals--audio)
- [Art and Audio Assets](#art-and-audio-assets)
- [Build & CI](#build--ci)
- [Project Layout](#project-layout)
- [Editing Levels](#editing-levels)
- [Roadmap / Planned Features](#roadmap--planned-features)
- [Not Included](#not-included)

---

## Gameplay Features

- **50 handcrafted stages** across five worlds: Neon Grid, Crimson Circuit, Azure Vault, Magenta Maze, and Golden Core.
- **Linear campaign progression** with local save data.
- **Automatic blade launch:** each stage begins with the blade flying into the arena.
- **Ricochet physics:** enemy impacts reverse the blade’s horizontal direction, sending it back toward the player.
- **Player-controlled left shield:** touch and drag vertically anywhere on the screen to move the shield up or down.
- **Landscape orientation:** the app opens and stays in horizontal fullscreen mode, giving the arena more width for tracking enemies and catching returns.
- **Instant-loss rule:** if the returning blade passes the shield at the left edge, the run ends immediately.
- **Enemy waves:** every stage creates a growing formation of enemies, with tougher enemy types and multi-hit targets in later stages.
- **Score, impact effects, and catch streaks:** hits, catches, and ricochets use visual and audio feedback.
- **Three difficulty levels:** Easy gives the shield a larger catch area, Normal is balanced, and Hard uses a smaller shield with a faster blade.
- **Pause, retry, stage selection, and victory/failure screens.**
- **Clear every enemy to finish the stage.**
- **Use Ⅱ in the top-right corner to pause.**

## Worlds & Stages

| World | Theme | Stage Range | Notes |
|---|---|---|---|
| Neon Grid | Cyan / neon | 1–10 | Introductory enemy formations |
| Crimson Circuit | Red / industrial | 11–20 | Faster blade, tighter catches |
| Azure Vault | Blue / icy | 21–30 | Multi-hit enemies appear |
| Magenta Maze | Purple / chaotic | 31–40 | Dense waves and mixed hazards |
| Golden Core | Gold / endgame | 41–50 | Toughest formations and final challenges |

- **Linear unlocking:** complete a stage to unlock the next.
- **Local saves:** campaign progress, score stars, and settings are stored on-device.
- **Stage select:** replay any unlocked stage.
- **Score stars:** earn stars based on performance.

## Enemies & Hazards

### Enemy Types

- **Drone** — basic flying enemy.
- **Turret** — stationary or slow-moving threat.
- **Slime** — bouncing or splitting enemy.
- **Tougher variants** — appear in later stages.
- **Multi-hit targets** — require multiple blade impacts.

### Hazards

- `spikes`
- `laser`
- `moving_hazard`
- `sticky_wall`
- `teleporter` — requires a pair.
- `power_up`

## Power-ups & Pickups

- `power_up` hazard type is supported in level JSON.
- Planned power-ups include shield expand, slow time, multi-catch, score multiplier, and more.

## Progression & Customization

- **Three selectable color themes:** Cyan, Green, Violet.
- **Saved campaign progress.**
- **Score stars.**
- **Sound settings** control music and effects.
- **Vibration settings.**
- **Local high scores** per stage.

## Controls

0. Play in horizontal (landscape) mode; rotate your device sideways if auto-rotate is disabled.
1. The blade launches automatically when a stage starts.
2. Touch anywhere and drag **up or down** to move the glowing shield on the left wall.
3. When the blade hits an enemy, it bounces back. Catch it by moving the shield into its path.
4. If the blade slips past the shield and exits on the left, you lose immediately.
5. Clear every enemy to finish the stage; use **Ⅱ** in the top-right corner to pause.

## Difficulty Levels

| Difficulty | Shield Size | Blade Speed | Notes |
|---|---|---|---|
| Easy | Larger catch area | Normal | More forgiving |
| Normal | Balanced | Balanced | Standard experience |
| Hard | Smaller shield | Faster blade | For experts |

## Visuals & Audio

- **Custom Canvas renderer** for gameplay and UI.
- **Bundled game art and UI:** menu/gameplay backgrounds, game logo, blade sprites, drone/turret/slime sprites, buttons, banners, and impact effects.
- **Tightly framed game art:** transparent outer margins are cropped when PNGs load, so buttons, icons, blades, and enemies use their visible bounds without re-encoding or inflating the original image files.
- **Soundtrack and SFX:** looping menu/gameplay/victory/game-over music plus blade launch, ricochet, enemy-hit, and UI sounds.
- **The Sound setting controls music and effects.**
- **Impact effects:** screen shake, particles, and flashes.
- **Catch streaks:** visual and audio feedback for consecutive catches.

## Art and Audio Assets

The build includes the original repository-level `Images/` and `SoundTrack/` folders through the Android asset source sets. Do not rename these folders or the filenames referenced by `MainActivity.kt`. The provided `Images/App Icon & Logo/App Icon.png` is copied to generated `mipmap-nodpi/ic_launcher.png` during the Gradle build and is used as the installed app icon.

## Build & CI

Requires JDK 17. In Android Studio, open the repository and sync Gradle; alternatively, run from the repository root with Gradle 8.9:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

The [GitHub Actions page](https://github.com/AngmarX-dev/Bounce-Blade/actions) contains the latest build runs and the downloadable debug APK artifact.

### CI Pipeline

- Validates assets and 50 level files.
- Runs unit tests.
- Builds the debug APK.
- Uploads the APK as an artifact.

## Project Layout

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

## Editing Levels

Edit `app/src/main/assets/levels/level_001.json` through `level_050.json`. Coordinates are normalized from 0.0 to 1.0. Supported hazards are `spikes`, `laser`, `moving_hazard`, `sticky_wall`, `teleporter`, and `power_up`. Each level declares its world and target/switch requirements; teleporters need a pair.

## Roadmap / Planned Features

The following features are planned or under consideration. They are **not** part of the current offline playable campaign unless explicitly listed above.

### Game Modes

- Endless mode
- Time attack
- Zen / practice mode
- Daily challenge
- Weekly challenge
- Boss rush
- Custom level sharing
- Local co-op
- Online multiplayer
- Speedrun mode

### Progression & Meta

- Achievements
- Trophies
- Online leaderboards
- Cloud saves
- Cross-device sync
- Player profiles
- Stats tracking
- Replay system
- Ghost replays
- Photo mode
- Replay export
- Seasonal events
- Battle pass (optional)
- Unlockable skins

### Gameplay Expansion

- More worlds (10+)
- Boss battles
- Procedural level generation
- Dynamic difficulty
- Power-ups: shield expand, slow time, multi-catch, magnet, score multiplier, extra life, blade speed, piercing
- Enemy types: armored, shielded, healing, splitting, teleporting, explosive, sniper
- Hazards: moving spikes, rotating lasers, gravity wells, portals, conveyor belts, breakable walls, switches, keys, doors
- Environmental effects: wind, ice, lava, water
- Blade upgrades
- Shield abilities
- Combo system expansion
- Perfect catch bonus
- Score multiplier chains

### Customization & Accessibility

- In-game level editor UI
- Theme editor
- Blade skins
- Shield skins
- Enemy skins
- Soundtrack player / jukebox
- Localization (i18n)
- Colorblind modes
- Screen reader support
- Haptic feedback intensity
- Remappable controls
- Left-handed mode
- One-handed mode
- Larger UI option
- Battery saver
- Performance mode
- High refresh rate support
- Variable aspect ratio
- Foldable support
- Android TV support
- Chromebook / tablet optimization
- Controller support
- Keyboard support

### Platform & Backend

- Online leaderboard backend
- Daily challenge server
- Cloud save backend
- Advertising
- In-app purchases
- Analytics
- Crash reporting
- Beta channel
- Modular asset packs
- Cross-platform saves

## Not Included

This is an offline playable campaign. Online leaderboards, cloud saves, an in-game level-editor UI, ghost replays, daily-challenge servers, advertising, and in-app purchases still require additional implementation and backend configuration.
