# Bounce Blade — Kotlin Android Edition

This is a native Kotlin/Android port of the supplied standalone HTML game. It uses a custom Android Canvas renderer and has no external runtime assets.

- Landscape touch gameplay: drag vertically to steer the shield while the blade launches and ricochets automatically.
- 50 deterministic stages across Neon Grid, Crimson Circuit, Azure Vault, Magenta Maze and Golden Core.
- Easy, Normal and Hard difficulty; drones, slimes, turrets, hazards and power-ups.
- Score, combo/catch streaks, lives, stars, local campaign saves, stage select, pause, retry, victory and game-over screens.
- Neon vector graphics, particles, hit feedback and native tone effects.

Build command: `gradle testDebugUnitTest assembleDebug`.
The GitHub Actions workflow publishes the APK as a release asset after a successful build.
