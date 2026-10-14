# Developer checklist

Use this checklist while completing the prototype.

## Gameplay modules
- [ ] Move the current Canvas prototype into the separated renderer, session, and physics modules.
- [ ] Connect `LevelRepository` to JSON parsing and validation.
- [ ] Make the switch activate the exit only when the intended conditions are met.
- [ ] Add deterministic collision tests for enemies, crates, hazards, and arena walls.
- [ ] Add pause, restart, and progression flows.

## Art and audio
- [ ] Add blade/enemy/crate/exit/switch sprites under `app/src/main/assets/art/sprites/`.
- [ ] Add arena backgrounds under `app/src/main/assets/art/backgrounds/`.
- [ ] Add music and sound effects under `app/src/main/assets/audio/`.
- [ ] Check and document the license for every bundled third-party asset.

## Release checks
- [ ] Keep `:app:testDebugUnitTest` and `:app:assembleDebug` green in GitHub Actions.
- [ ] Test touch input on a physical Android device and at more than one screen size.
- [ ] Test offline behavior before adding online services.
- [ ] Never commit signing keys, API tokens, or backend secrets.
