# ⚔️ Bounce Blade

**Bounce Blade** is an offline Android arcade game built with Kotlin and a custom Canvas renderer. A spinning blade launches automatically from the left, hits enemies and ricochets back. You move a glowing shield along the left wall to catch every return.

> **Platform:** Android (offline)  
> **Language:** Kotlin  
> **Rendering:** Custom Canvas  
> **Orientation:** Landscape fullscreen  
> **Build:** Gradle 8.9 / JDK 17

---

## Table of Contents

- [The Story](#the-story)
- [Gameplay Features](#gameplay-features)
- [Worlds & Stages](#worlds--stages)
- [Enemies & Hazards](#enemies--hazards)
- [Power-ups & Pickups](#power-ups--pickups)
- [Progression & Customization](#progression--customization)
- [Controls](#controls)
- [Difficulty Levels](#difficulty-levels)
- [Visuals & Audio](#visuals--audio)
- [Physics Runtime](#physics-runtime)
- [Art and Audio Assets](#art-and-audio-assets)
- [Build & CI](#build--ci)
- [Project Layout](#project-layout)
- [Editing Levels](#editing-levels)
- [Roadmap / Planned Features](#roadmap--planned-features)
- [Not Included](#not-included)

---

## The Story

In the beginning, there was only the **Lumen Grid**—a vast, shimmering tapestry of light and data, woven from the dreams of a thousand forgotten stars. At its heart burned the **Golden Core**, the first spark, the source of all creation. To protect this fragile light, the Guardians were forged: beings of will and reflection, each wielding a shield of pure intent.

But the Grid was not alone. From the edges of the void came the **Corruption**—a creeping static that twisted the Grid’s own energies into ravenous enemies. To fight it, the Guardians created the **Bounce Blade**: a weapon of precision, a shard of the Core itself, designed to ricochet through the Corruption and return to its master’s hand.

For eons, the balance held. But the Corruption learned. It poisoned the Blade, filling it with wild, uncontrollable momentum. Now, when the Blade is launched, it does not simply return—it rebounds with the force of every impact, a runaway star that can shatter the Grid if it escapes.

You are the last Guardian. Your shield is the final wall between order and oblivion. Across ten worlds—the **Neon Grid** of logic, the **Crimson Circuit** of passion, the **Azure Vault** of memory, the **Magenta Maze** of chaos, the **Golden Core** of creation, the **Obsidian Rift**, the **Solar Flare**, the **Quantum Abyss**, the **Storm Citadel**, and the **Celestial Forge**—you must guide the Blade. Destroy the Corruption. Catch the light. If the Blade slips past you, the darkness wins.

But you are not alone. The Blade remembers its purpose. Every enemy it strikes, every ricochet, every perfect catch strengthens the bond between you. The Grid whispers in light and sound, and the music of the spheres becomes your battle hymn. Catch the Blade. Save the light. Become the legend.

---

## Gameplay Features

- **100 handcrafted stages** across ten worlds: Neon Grid, Crimson Circuit, Azure Vault, Magenta Maze, Golden Core, Obsidian Rift, Solar Flare, Quantum Abyss, Storm Citadel, and Celestial Forge.
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
| Golden Core | Gold / endgame | 41–50 | Tough formations and boss challenges |
| Obsidian Rift | Dark / spectral | 51–60 | More complex enemy patterns |
| Solar Flare | Orange / solar | 61–70 | Heat and movement pressure |
| Quantum Abyss | Blue / quantum | 71–80 | Distorted motion and spatial hazards |
| Storm Citadel | Blue / storm | 81–90 | Stronger mixed enemy waves |
| Celestial Forge | Gold / cosmic | 91–100 | Final campaign challenges |

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

- **Six selectable color themes:** Cyan, Green, Violet, Crimson, Solar, and Ocean.
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

## Physics Runtime

The gameplay blade is advanced by an Android-safe **Fhysics** rigid-body adapter with substepped motion, material restitution and friction, angular motion, gravity/force support, and circle-contact impulses. The shield side remains open so Bounce Blade's existing catch and fail conditions remain intact. See [Fhysics integration notes](docs/physics-fhysics.md) and [third-party notices](THIRD_PARTY_NOTICES.md) for scope and MIT attribution.

## Art and Audio Assets

The build includes the original repository-level `Images/` and `SoundTrack/` folders through the Android asset source sets. Do not rename these folders or the filenames referenced by `MainActivity.kt`. The provided `Images/App Icon & Logo/App Icon.png` is copied to generated `mipmap-nodpi/ic_launcher.png` during the Gradle build and is used as the installed app icon.

## Build & CI

Requires JDK 17. In Android Studio, open the repository and sync Gradle; alternatively, run from the repository root with Gradle 8.9:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
