# ⚔️ Bounce Blade — Unity Edition

This branch adds a Unity 2D project under UnityProject. It is separate from the Kotlin Android edition and leaves the existing Kotlin branch intact.

**Engine:** Unity 2022.3 LTS · **Language:** C# · **Target:** Android landscape · **Genre:** neon 2D arcade action

## Open and build

1. Install Unity Hub and Unity 2022.3 LTS with Android Build Support, SDK/NDK Tools and OpenJDK.
2. Open the UnityProject folder from this repository in Unity Hub.
3. Allow Unity to import the included artwork and audio. The editor helper creates the main scene and adds it to Build Settings if needed.
4. In the Unity Editor, choose Bounce Blade → Build Android APK. The output is UnityProject/Build/Android/BounceBlade.apk.

## Features

- 100 stages across ten worlds with saved progression.
- Auto-launching ricochet blade, touch-controlled shield, catches, score, combos and lives.
- Drones, slimes, turrets, boss stages, breakable crates and health bars.
- Twin auto-target mini-guns that aim individually; blade and gun hits reduce target health by one-third per hit.
- Ten themed mini-gun art variants, original blade/enemy graphics, impact effects and camera shake.
- Spikes, laser timing, moving hazards, sticky walls, paired portals, switches and power-ups.
- Three-star rewards, local high scores, shards, shop upgrades and multi-catch.
- Six themes, Easy/Normal/Hard settings, story/help pages, pause/retry/win/fail screens.
- Original backgrounds, logos, sprites, UI, banners, particles, music and gameplay SFX.

## GitHub Actions

The Unity workflow checks source and bundled resources. Building an APK on GitHub requires a valid Unity license XML configured as the repository Actions secret UNITY_LICENSE. If it is not present, the workflow skips the APK-build step and explains why. The Kotlin version and its existing APK remain separate from this branch.

See UnityProject/README.md for setup and UnityProject/Tools/MANUAL_TESTS.md for the smoke-test checklist.
