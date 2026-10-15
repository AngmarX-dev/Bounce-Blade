# Campaign levels

The offline campaign includes `level_001.json` through `level_050.json`, grouped into five worlds of ten levels:

1. **Neon Grid** — introductory ricochet practice without enemies.
2. **Crimson Circuit** — drones, crates and moving hazards.
3. **Azure Vault** — rotating turrets and projectiles.
4. **Magenta Maze** — splitting slimes, sticky walls and paired teleporters.
5. **Golden Core** — shielded enemies, mixed hazards, power-ups and the multi-hit Core Guardian at level 50.

Each JSON has `id`, `name`, `world`, `maxBounces`, `playerSpawn`, `exit`, `switch`, `enemies`, `crates`, `hazards`, `requiresAllTargets` and `requiresSwitch`. Coordinates and sizes are normalized to 0..1. Hazard types: `spikes`, `laser`, `moving_hazard`, `sticky_wall`, `teleporter`, `power_up`. A teleporter level requires at least two portal entries; power-ups add two bounces.
