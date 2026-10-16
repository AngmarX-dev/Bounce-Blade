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
- [Roadmap Feature Status](#roadmap-feature-status)
- [Not Included](#not-included)

---

## Gameplay Features

- **100 stages across ten worlds**: Neon Grid, Crimson Circuit, Azure Vault, Magenta Maze, Golden Core, Obsidian Rift, Solar Flare, Quantum Abyss, Storm Citadel, and Celestial Forge.
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
- **Use Ⅱ to pause**; the pause location adapts to left-handed layout.
- **Multiple playable modes:** campaign, endless, time attack, Zen/practice, daily and weekly challenges, boss rush, speedrun, procedural arenas, and two-player local co-op.
- **Progression and meta:** local achievements, profile and lifetime stats, per-stage score tables, a seasonal event pass, unlockable cosmetics, and persistent coin-funded blade upgrades.
- **Replay and sharing tools:** locally saved ghost traces, replay export, photo mode, save backup import/export, and editable/shareable level drafts.
- **Combat variations:** armored/shielded enemies, healers, explosive enemies, teleporters, snipers and boss targets; expanded hazards and world-specific environmental effects.
- **Accessibility and input:** English/Persian UI, colorblind palette, high contrast, reduced motion, adjustable haptics, remappable keyboard controls, gamepad input, left-handed and one-handed layouts, and larger UI.
- **Performance options:** battery saver and refresh-rate-aware frame pacing.

## Worlds & Stages

| World | Theme | Stage Range | Notes |
|---|---|---|---|
| Neon Grid | Cyan / neon | 1–10 | Introductory enemy formations |
| Crimson Circuit | Red / industrial | 11–20 | Faster blade, tighter catches |
| Azure Vault | Blue / icy | 21–30 | Multi-hit enemies appear |
| Magenta Maze | Purple / chaotic | 31–40 | Dense waves and mixed hazards |
| Golden Core | Gold / endgame | 41–50 | Toughest original campaign formations |
| Obsidian Rift | Black / violet | 51–60 | Advanced hazards and resilient enemies |
| Solar Flare | Solar / orange | 61–70 | Fast waves and aggressive patterns |
| Quantum Abyss | Deep blue | 71–80 | Teleporters and unstable formations |
| Storm Citadel | Storm / silver | 81–90 | High-pressure enemy combinations |
| Celestial Forge | Gold / white | 91–100 | Final gauntlet and endgame challenge |

- **Linear unlocking:** complete a stage to unlock the next.
- **Local saves:** campaign progress, score stars, and settings are stored on-device.
- **Stage select:** replay any unlocked stage.
- **Score stars:** earn stars based on performance.

## Enemies & Hazards

### Enemy Types

- **Drone, turret and slime** — the original roster, including splitting and multi-hit variants.
- **Armored and shielded enemies** — require additional impacts.
- **Healer** — restores nearby hostile units.
- **Explosive** — advances quickly and threatens a wall breach.
- **Teleporter** — blinks between arena positions.
- **Sniper** — a more durable, high-pressure enemy.
- **Final boss** — a multi-hit target in the final campaign world.

### Hazards

Level JSON and gameplay support spikes and moving spikes, standard and rotating lasers, moving hazards, sticky walls, portals/teleporters, gravity wells, conveyors, breakable walls, switches, keys and doors, plus pickups. The procedural generator can place supported hazard variants in its generated arenas. Teleporter pairs are validated by CI.

### Environmental Effects

Wind gusts, ice drift, water drag and timed lava surges vary movement or add pressure in later worlds. World-specific visual effects reinforce the environment theme.

## Power-ups & Pickups

Playable pickups include shield expansion, slow time, multi-catch, coin magnet, score multiplier, extra life, blade speed and piercing. They apply temporary effects or immediate rewards during a run.

Blade Forge also contains persistent, coin-funded blade and shield upgrades, including Nova Pulse and improvements to rebound speed, shield size, pulse radius and coin rewards.

## Progression & Customization

- **Ten worlds and 100 campaign stages**, with linear unlocking and on-device progress.
- **Difficulty settings** and performance stars; perfect catches, combo chains and score multipliers add replay value.
- **Local achievements, profile name and stats**, including runs, clears, failures, play time, stars and best combo.
- **Local leaderboard** by score and difficulty.
- **Seasonal event / pass** with a locally tracked progress path and coin reward.
- **Themes and unlockable cosmetics:** blade, shield and enemy styles; cosmetics can be unlocked using earned coins.
- **Blade Forge** upgrades persist on the device.
- **English/Persian localization**, colorblind palette, high-contrast and reduced-motion options.
- **Soundtrack jukebox**, adjustable haptic feedback, keyboard remapping and accessibility options.
- **Replay traces, ghost paths and replay export**, along with photo mode.
- **Save portability:** export and import a JSON save backup or custom level draft. Transfer the file or text through a service you choose; the game does not automatically upload saves.

## Controls

1. Play in horizontal (landscape) mode; the playfield uses normalized coordinates to adapt to different screen sizes.
2. The blade launches automatically. Drag vertically to move the shield and catch the blade on its return.
3. In two-player local co-op, use two simultaneous touch points to control the two shields.
4. Use the pause button to pause. Its location adapts to the left-handed layout setting.
5. Keyboard and gamepad key events can move the shield and pause. Remap supported keys from Help / Controls.
6. Collect pickups, hit targets, and build combos. Missing a catch normally ends the run; Zen/practice is designed for retrying without losing the run.

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
- **Catch streaks, perfect catches, combo multipliers, environmental effects and multi-stage enemy damage** provide visual and audio feedback.
- **Replay traces and photo mode** let players save a run path or export a gameplay image.
- **Refresh-aware rendering and reduced-motion / battery-saver options** allow a lower frame rate where appropriate.

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

- Validates assets, all 100 campaign level files and supported hazard data.
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
    game/editor/                    level-draft support and custom arena sharing
    game/daily/                     daily challenge data model
    game/leaderboard/               online leaderboard contract
  assets/levels/                    100 campaign files plus template
  res/                              Android theme, colors and launcher icon
  test/                             unit tests
```

## Editing Levels

Edit `app/src/main/assets/levels/level_001.json` through `level_100.json`. Coordinates are normalized from 0.0 to 1.0. Supported hazards include spikes, moving spikes, rotating lasers, gravity wells, portals, conveyors, breakable walls, switches, keys, doors, and power-ups. The CI validates level IDs, world mapping, normalized positions and required portal pairs. The in-game Level Lab can save/share a custom draft and run a play test.

## Roadmap Feature Status

The original roadmap has been checked against the current playable build. These features now have in-game implementations, generally using local data so the game remains playable offline.

### Implemented Game Modes

- Endless mode, time attack, Zen/practice, daily and weekly seeded challenges, boss rush, speedrun and procedural generation.
- Local two-player co-op with independent touch shields.
- Custom level draft editing, import/export and play testing.

### Implemented Progression & Meta

- Local achievements/trophies, player profile, stats tracking and score tables.
- Local save persistence, JSON backup import/export and manual cross-device transfer.
- Replay/ghost trace storage and export, photo mode, local seasonal event/pass and unlockable cosmetics.

### Implemented Gameplay Expansion

- Ten worlds and 100 campaign levels, with a final boss.
- Dynamic difficulty, blade upgrades, shield abilities, combo chains, perfect-catch bonuses and score-multiplier chains.
- Shield expand, slow time, multi-catch, magnet, score multiplier, extra life, blade speed and piercing power-ups.
- Armored, shielded, healing, splitting, teleporting, explosive and sniper enemy variants.
- Moving spikes, rotating lasers, gravity wells, portals, conveyors, breakable walls, switches, keys and doors.
- Wind, ice, lava and water environmental effects.

### Implemented Customization & Accessibility

- In-game Level Lab, themes, blade/shield/enemy skins and soundtrack jukebox.
- English/Persian localization, colorblind palette, high contrast, screen-reader announcements, adjustable haptics and remappable controls.
- Left-handed and one-handed modes, larger UI, reduced motion, battery saver and refresh-rate-aware frame pacing.
- Responsive normalized-coordinate layout, plus keyboard and gamepad key-event input.

### Services That Still Need External Infrastructure

These items are visible in the game’s Online / Platform Services screen as unavailable until the required service is configured; the offline client does not pretend to provide a global service:

- **Online leaderboards, online multiplayer and a challenge server** need hosted backend services.
- **Automatic cloud saves, authentication and live cross-device sync** need a backend/account system. Manual export/import is available now.
- **Advertising and in-app purchases** need a configured store SDK, product IDs, policy setup and release configuration.
- **Analytics and crash reporting** need a selected SDK and project configuration.
- **Beta channel and downloadable modular asset packs** need a distribution/update pipeline.
- **Foldable, Android TV and Chromebook/tablet support** still need dedicated device-layout and input testing. Generic responsive layout and keyboard/gamepad events are present, but those form factors have not been verified on physical hardware.

## Not Included

The build is still an offline-first Android game. Global/online services, authentication, ad and purchase integrations, automatic cloud sync, a hosted challenge/leaderboard backend and platform-specific device certification are not enabled without external accounts, SDKs, service endpoints and testing. Local alternatives (scores, challenges, saves, replays, events and profiles) are implemented in-game.
