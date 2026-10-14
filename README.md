# ⚔️ Bounce Blade

A neon-on-dark 2D physics puzzle for Android. Aim a spinning blade, launch it, and use ricochets to clear each arena.

## Prototype features
- Portrait, one-handed touch controls: drag back to aim and release to launch.
- Two-finger slow time with a rechargeable meter.
- Neon arenas, blade physics, enemies, breakable crates, switch, exit, and limited bounces.
- Ten starter arenas with retry and progression.
- GitHub Actions builds a debug APK artifact.

## Build
Open in Android Studio with JDK 17 and sync Gradle, or run `gradle :app:assembleDebug` with Gradle 8.9. Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Roadmap
The full 100+ level campaign, daily challenge backend, global leaderboards, ghost replays, community editor, ads, rewarded retries, and in-app purchases are not included yet. This prototype contains no advertising or billing SDK.
