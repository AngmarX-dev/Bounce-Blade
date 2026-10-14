# Campaign levels

The offline campaign includes `level_001.json` through `level_020.json`. Each file is loaded at runtime by the game view. Keep the numeric ID consistent with the three-digit filename.

Each JSON level supports:
- `id`, `name`, and `maxBounces`
- `playerSpawn`, `exit`, and `switch` points
- `enemies` and `crates` arrays with normalized `x` and `y`
- a `hazards` array with `type`, `x`, `y`, `width`, and `height`

Coordinates are normalized from 0.0 to 1.0. Keep the player spawn outside the spike hazard and avoid placing targets directly on top of the switch or exit. The loader falls back to a safe generated layout if a level file cannot be parsed.
