# Bounce Blade — Unity Edition

This is the Unity 2D project for the offline Android arcade game Bounce Blade. It reuses the repository’s artwork and soundtrack and is separate from the Kotlin Android build.

## Open in Unity

- Unity Editor: 2022.3 LTS (see ProjectSettings/ProjectVersion.txt).
- Open the UnityProject folder in Unity Hub.
- Install Android Build Support, Android SDK & NDK Tools, and OpenJDK from Unity Hub.
- The editor helper creates Assets/Scenes/Main.unity and registers it in Build Settings if missing.
- Target: Android, landscape, minimum Android 7.0 (API 24), ARM64.

## Implemented gameplay systems

- 100 stages across ten worlds: Neon Grid, Crimson Circuit, Azure Vault, Magenta Maze, Golden Core, Obsidian Rift, Solar Flare, Quantum Abyss, Storm Citadel and Celestial Forge.
- Automatic blade launch, vertically draggable shield, ricochet boundaries, return catches, combos, lives and win/fail states.
- Drones, slimes, turrets, boss stages and destructible crates with health bars.
- Twin auto-aim mini-guns and blade damage. Each blade impact or mini-gun shot removes one-third of maximum target health, rounded up.
- Spikes, timed lasers, moving hazards, sticky walls, paired portals, switches and power-ups.
- Scores, catch streaks, three-star ratings, high scores, stage unlocks and persistent progress.
- Shard currency; upgrades for mini-guns, blade speed, shield width, slow-time capacity and multi-catch.
- Six themes, Easy / Normal / Hard, audio/vibration settings, story/help, pause/retry, victory and defeat screens.
- Original logos, menus, backgrounds, player/enemy sprites, mini-gun skins, buttons, banners, particles, music and SFX are bundled under Assets/Resources/BounceBlade.

## Build an APK

From the Unity Editor, choose Bounce Blade → Build Android APK. The output is Build/Android/BounceBlade.apk in UnityProject.

GitHub Actions validates source and asset paths on every push. A cloud APK build requires a valid Unity license XML stored as the repository Actions secret UNITY_LICENSE. When that secret is configured, the Unity Android APK workflow builds the Android player and uploads an APK artifact. Without a license secret, the workflow reports that validation passed but does not claim an APK was produced.

## Project structure

- Assets/Scripts/BounceBladeGame.cs — runtime game, UI, controls, physics, combat, progression and audio.
- Assets/Editor/BounceBladeBuild.cs — auto scene setup and Android build command.
- Assets/Resources/BounceBlade/Images — original art.
- Assets/Resources/BounceBlade/SoundTrack — original music and SFX.
- Tools/validate_project.py — source/resource validation.
- Tools/MANUAL_TESTS.md — device/manual smoke-test checklist.
