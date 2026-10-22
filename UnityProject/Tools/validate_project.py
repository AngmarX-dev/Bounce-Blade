#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
required = [
    "Assets/Scripts/BounceBladeGame.cs", "Assets/Editor/BounceBladeBuild.cs",
    "Packages/manifest.json", "ProjectSettings/ProjectVersion.txt",
    "ProjectSettings/EditorBuildSettings.asset", "ProjectSettings/ProjectSettings.asset",
    "README.md",
]
missing = [item for item in required if not (ROOT / item).is_file()]
if missing:
    sys.exit("Missing Unity project files: " + ", ".join(missing))

source = (ROOT / "Assets/Scripts/BounceBladeGame.cs").read_text(encoding="utf-8")
markers = [
    "MaxStages = 100", "Celestial Forge", "TickGuns", "BuyUpgrade", "StageSelect",
    "Settings", "Story", "CheckHazards", "Pickup", "PlayerPrefs", "Handheld.Vibrate",
    "AudioAt", "ScreenOrientation.LandscapeLeft", "multiCatch", "TickBolts", "Damage",
]
missing_markers = [marker for marker in markers if marker.lower() not in source.lower()]
if missing_markers:
    sys.exit("Missing expected game systems: " + ", ".join(missing_markers))

manifest = (ROOT / "Packages/manifest.json").read_text(encoding="utf-8")
if "com.unity.ugui" not in manifest:
    sys.exit("Unity UI dependency is missing.")
editor = (ROOT / "Assets/Editor/BounceBladeBuild.cs").read_text(encoding="utf-8")
for marker in ["BuildPipeline.BuildPlayer", "Assets/Scenes/Main.unity", "BuildTarget.Android"]:
    if marker not in editor:
        sys.exit("Unity Android build entry point missing: " + marker)

assets = ROOT / "Assets/Resources/BounceBlade"
resource_paths = [
    "Images/App Icon & Logo/Game Logo.png", "Images/App Icon & Logo/App Icon.png",
    "Images/Backgrounds & Environments/Menu Background.png",
    "Images/Backgrounds & Environments/Gameplay Background.png",
    "Images/Backgrounds & Environments/Victory Background.png",
    "Images/Gameplay Sprites/Blade.png", "Images/Gameplay Sprites/Enemy Drone.png",
    "Images/Gameplay Sprites/Enemy Slime.png", "Images/Gameplay Sprites/Enemy Turret.png",
    "Images/Gameplay Sprites/Crate.png", "Images/UI Elements/Button.png",
    "Images/UI Elements/UI Panel.png", "Images/Effects & Particles/Explosion.png",
    "Images/Effects & Particles/Slice Effect.png",
    "Player Miniguns/Gameplay/Minigun_NeonGrid.png",
    "Player Miniguns/Gameplay/Minigun_CelestialForge.png",
    "Player Miniguns/Menu/Minigun_NeonGrid.png",
    "SoundTrack/Core Gameplay SFX/Blade Launch.wav",
    "SoundTrack/Core Gameplay SFX/Enemy Slice (Variation 1).wav",
    "SoundTrack/Music & Ambience/Main Menu Theme.wav",
    "SoundTrack/Music & Ambience/Gameplay Background.wav",
    "SoundTrack/Music & Ambience/Victory Theme.wav",
]
missing_assets = [p for p in resource_paths if not (assets / p).is_file()]
if missing_assets:
    sys.exit("Missing bundled game assets: " + ", ".join(missing_assets))
count = sum(1 for p in assets.rglob("*") if p.is_file())
if count < 80:
    sys.exit("Expected the image/audio collection, found only " + str(count) + " files.")
print("Unity source/resource validation passed.")
print("100-stage campaign, ten worlds, touch input, targets, mini-guns, hazards, upgrades, saves and audio routes found.")
print("Bundled Resources files:", count)
