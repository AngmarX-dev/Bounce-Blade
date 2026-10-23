# Fhysics integration in Bounce Blade

Bounce Blade's Android build is rendered by a custom Canvas view and uses Gradle/Kotlin. Upstream Fhysics is a Kotlin rigid-body engine, but its desktop app also imports JavaFX/AWT UI and rendering code. Those desktop dependencies are not appropriate for the Android runtime, so this integration adapts the simulation concepts into an Android-safe engine rather than adding the desktop application as a Maven dependency.

## Live gameplay integration

- `GameView.simulateWallGuardStep` advances the blade through `FhysicsWorld.stepBody`.
- Motion uses semi-implicit Euler integration with bounded substeps to reduce tunneling at the arena edges.
- The blade has a mass, circular radius, restitution, static/dynamic friction material, angle, and angular velocity.
- Solid arena edges resolve impulses with restitution and friction. The player's side is deliberately unconstrained; the existing shield-catch / missed-blade loss rules remain authoritative.
- The world exposes force/gravity integration and a circle-circle impulse solver for dynamic bodies. Game events such as piercing enemy hits, portals, switches, and lethal hazards stay under Bounce Blade's gameplay rules rather than being converted into solid impacts.

## Scope

This is an Android-focused gameplay adapter, not a claim that every upstream desktop feature (JavaFX editor, arbitrary concave polygon decomposition, or upstream QuadTree editor/debug views) was transplanted. The upstream project and MIT license are credited in `THIRD_PARTY_NOTICES.md`.

## Verification

Run `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug`. The Fhysics unit tests cover boundary restitution, the open player-side boundary, circle contact response, non-overlapping bodies, and angular integration.
