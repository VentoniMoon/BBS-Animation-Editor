# BBS Animation Editor — AI ASSISTANT integration (implementation plan)

Status: **design and first safe offline command core only**. No LLM, external API,
automatic scene mutation or background agent is connected.

## Goals

1. Make routine authoring **zero-token**, without invoking any external model.
2. Expose all edit operations through the **existing controllers**, never through
   mouse-coordinate automation, scripted keypresses, Java reflection or shell.
3. Keep optional remote-model context short, bounded and task-specific.
4. Plan -> validate -> preview -> user approval -> atomic apply -> undo.
5. Isolate expensive MotionLab/video analysis from routine character editing.
6. Preserve Java 8 / Forge 1.12.2 compatibility and existing saved scenes.

## Architecture

```text
AI ASSISTANT tab / Ctrl+Shift+A
           |
  Intent router (Java 8, offline)
       /                    \
known typed task           complex language / video request
       |                        |
local action planner       context snapshot (selected actor,
(no model call)              requested frame range, bone summary)
       |                        |
       |                  optional model adapter
       |                 [disabled until configured]
       |                        |
       +-------- validated typed proposals ----------+
                                 |
                     preview of changed keys/tracks
                                 |
                           user APPROVE
                                 |
                   atomic EditorActionTransaction
                   (existing trusted controllers)
                                 |
                         Undo / Save
```

### Offline first (zero-token)

Existing `EditorAssistantCore` already has a primitive typed allowlist:
HELP, INSPECT, GOTO_FRAME, CREATE_BONE_KEY, PIN_BONE, UNPIN_BONE.
Add deterministic local commands before enabling any model:
set interpolation, select bone, duplicate/mirror selected keys, batch-shift
keyframes, inspect invalid collisions, and run scene integrity reports.
All are regular editor controller operations.

**Never call a model** for navigation, selecting a part, creating a key,
hover highlighting, copying already-known transforms, changing a numerical
property, or displaying current time.

### Optional remote model (only on explicit user request)

- Model returns a **versioned JSON action plan**, *not* arbitrary text to
  interpret as a shell command or Java source.
- Allowlisted action names, primitive numeric arguments, expected actor ID,
  optional expected source keyframe revision, and target frame range.
- Max action count per proposal and per batch, limits on frame ranges,
  and a hard cap on source payload/output size. Each limit is user-visible.
- Preflight dry-run computes a diff and any errors before approval.
- Approval is required for **every scene-changing batch**, including when
  the model asks to bypass approval.
- A stale actor/scene ID or changed revision invalidates the proposal.
- Undo groups the whole batch as one reversible transaction; no partial
  scene mutation on validation failure.
- No direct file deletion, program execution, networking or secret access
  from model output. The only writer is the editor's own trusted saver.
- External service credentials stay outside `editor.dat` and Record/NBT.

### Compact context packets

Send **only** the requested workspace/actor/bone/frame interval. Prefer:

```json
{
  "schema": 1,
  "mode": "POSE",
  "actorId": "<selected actor ID>",
  "bone": "<selected bone name>",
  "frame": 40,
  "range": [25, 55],
  "pose": {"rx": 0, "ry": 0, "rz": 0},
  "allowedActions": ["set_rotation", "create_key", "set_interpolation"],
  "revision": "<local scene/key hash>"
}
```

- Source exact numeric values from the active editor, never from guesses.
- For multi-actor work, include the actor count and **only** the actors
  requested, not all Character/Camera/Physics timelines.
- If needed, fetch more bounded detail in a second step (progressive
  disclosure), not a 500-tick NBT dump in every prompt.
- Reuse a cache keyed by scene revision and context range. Invalidate on edit.
- Keep a running local summary; do not resend the entire conversation or
  repeated 60 FPS video frames.
- Surface estimated/actual usage and a configurable user-side call budget.
  Usage estimates are advisory; actual cost depends on the chosen provider.
- No model call should happen on every tick, hover, drag or render frame.

### MotionLab: expensive jobs are explicit, not chat completions

Video -> candidate 2D detections -> BOBJ mapping -> 3D IK -> per-frame
contact validation -> preview-only scene diff -> approved export.
This is an **external/on-demand batch pipeline**, possibly a Python worker,
not a request to continuously drive the Minecraft GUI.
Deliver checkpoints (0–60, 60–120, etc.), separate input/output files and
measure against an unchanged reference camera. Reuse results between jobs.
A failed game-control attempt must not loop indefinitely or consume further
model calls; manual import and one user-captured render remain valid.

### UI: full workspace, retain shortcut

- Add a dedicated **AI ASSISTANT** workspace after stable core integration.
- Left: history / task queue / model connection status.
- Center: natural-language request, chosen actor(s), selected tick range.
- Right: structured proposed actions, warnings, keyframe diff,
  PREVIEW / APPROVE / REJECT / UNDO.
- Bottom: deterministic local diagnostics, runtime status, and token budget.
- Retain Ctrl+Shift+A as quick command launcher.
- Offline mode stays functional and is the default.
- Assistant must never silently enable physics experimental flags or
  modify the user's installed mods.

## Development stages

1. **Stage A — already partly present:** typed offline commands and
   preview-before-approval. Harden stale state and transaction semantics.
2. **Stage B:** public `EditorActionsAPI` backed by existing controllers,
   unit tests, grouped undo, read-only scoped context snapshots.
3. **Stage C:** dedicated AI ASSISTANT mode and safe local multi-step plans.
4. **Stage D:** opt-in configurable LLM provider behind JSON schema and
   budget guard. No implicit network calls.
5. **Stage E:** MotionLab batch integration with explicit input/output,
   async progress owned by the editor, bounded retries and cancellation.
6. **Stage F:** comparative visual review, then optional assisted animation.

## Acceptance tests

- Navigate and make a key entirely offline: **zero** model requests.
- Unknown request and failed preflight: **zero** mutations.
- After changing the selected scene/actor between PREVIEW and APPROVE,
  an old proposal cannot be executed.
- Every batch can be undone with one operation.
- A provider failure or quota exhaustion leaves the editor usable offline.
- 500+ tick scene doesn't cause the assistant to upload full NBT/video on
  a trivial single-bone command.
- Physics, camera and renderer continue to work with assistant disabled.
