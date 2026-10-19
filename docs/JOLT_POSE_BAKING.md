# Jolt -> Body Parts pose baking (experimental)

This is the first controlled capture/commit implementation. It does **not**
change the native Jolt JAR, the existing Weld mechanism, or live hair physics.

## Test workflow (Forge 1.12.2)

1. Start the editor with `-Dbbs.editor.physics.jolt.preview=true` and the
   working native Jolt JAR. Select the actor and its Chameleon **Body Part**
   that contains the configured physical hair chains.
2. Pause and seek to the beginning of the desired segment (ideally frame 0).
3. Press **Ctrl+Shift+B**: `Jolt bake ARMED`.
4. Start normal timeline playback. Keep the actor visible in Preview so that
   sequential Jolt frames are computed. Do not scrub during recording.
5. Pause. Press **Ctrl+Shift+B** again. The editor validates the collected
   frames and stores one ordinary Body Parts key per recorded physical bone
   per frame. It marks the editor dirty.
6. Save the project with Ctrl+S and use the existing Record export action
   to write the result back to Blockbuster. Replay with Jolt disabled.

The status is printed to `latest.log` with `[BBS Editor][Bake]` and
also sent to in-game chat when available.

## Safety invariants

* Only the selected Chameleon Body Part is sampled; no background actor key
  writes. Native Jolt must be requested, and at least one physics chain must
  be enabled when capture is armed.
* Each sample is taken from the Chameleon Euler pose **after** the native
  swing has been applied, not from unmodified animation keys or a guessed
  world direction. The captured editor key contains the clean source pose
  plus the observed physics Euler delta, expressed in **degrees**.
* While armed, capture uses discrete frame rotations (render-time quaternion
  interpolation is temporarily bypassed) for deterministic key data.
* Captures are buffered: no original keys change until successful commit.
  Missing frames, missing physics bones, non-finite rotations and
  incomplete coverage reject the transaction.
* Key values at existing timestamps are overwritten **only** after
  validation. Untargeted bones retain their keys. Position/scale of
  targeted keys come from their evaluated, pre-bake editor poses.
* Once committed, live physical chain toggles for the selected Body Part
  are disabled to avoid applying Jolt again on top of baked rotations.
  Re-enable them manually when starting a new simulation.
* Render-preview actor reset clears the uncommitted bake buffer.

## Known limitations

* Experimental workflow; compile and in-game tests are still required.
* The captured timeline matches the editor's **visible frozen Jolt pose**.
  The live preview coordinator deliberately publishes the preceding
  completed simulation step, so the displayed animation may be offset
  relative to the solver's internal tick. Compare exported playback frame by
  frame before relying on the result.
* Every recorded frame gets a key (20 keys per simulated second). Key
  reduction and final export optimization are not implemented.
* In case the actor/model is switched or the preview is reset mid-session,
  restart the capture rather than trying to splice scenes.
* The current export supports **bone transforms**, not deformed vertices:
  Weld still needs its own implementation, separately from pose baking.
* To compare against live physics, disable the live Jolt setting or use
  another copy of the original scene. Backup the Record before exporting.

## Isolated Java 8 smoke tests

The workflow `.github/workflows/jolt-bake-smoke.yml` compiles the
production `BodyPartPhysicsBakeController.java` against test-only stubs. It
checks deferred commit, duplicate render passes, gap rejection, changed
bone-coverage rejection and disabling live physics only after success.

The smoke harness is **not** a Forge/JNI integration test. A complete
`runClient` build and real Blockbuster export roundtrip remain required.
