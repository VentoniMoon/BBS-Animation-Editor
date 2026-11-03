# Full modpack + heavy world stress test

This profile is **opt-in**. Ordinary `runClient` continues using `run/`.
Stress sessions use `run-stress/`, which is ignored by Git.

## Stage the user's real modpack and map (Windows PowerShell)

**Close Minecraft before copying a world**, so region and level metadata are consistent.

From the editor repository root:

```powershell
.\tools\prepare-stress-test.ps1 `
  -ModsDirectory "D:\MinecraftPack\mods" `
  -WorldDirectory "D:\MinecraftPack\saves\MyHeavyWorld"
```

`WorldDirectory` is the directory containing `level.dat`.
The staging tool copies:
- all source mod JARs except an already installed BBS Animation Editor JAR
  (the development runtime supplies the editor);
- the complete selected world, including region, entity, data, and player files;
- config, shaderpacks, resourcepacks and selected custom assets from the modpack root.

Use `-PackRoot "D:\MinecraftPack"` if the pack root is not the parent of
the source `mods` folder. For a dry run, append `-WhatIf`.
Use `-SkipExtraAssets` when only mods + world are wanted.

The tool **refuses to overwrite** an existing `run-stress`. Move or rename
that test directory if preparing another independent snapshot.

Launch with:

```powershell
.\gradlew.bat runClient -PstressTest
```

For the original minimal development profile:

```powershell
.\gradlew.bat runClient
```

**Java 8 and Forge 1.12.2** remain required. The Gradle daemon's
`org.gradle.jvmargs=-Xmx3G` does not necessarily set the launched game's
heap. Configure the Minecraft JVM separately (e.g. 6–8 GiB only when physical
RAM allows it). Preserve enough RAM for the OS and GPU.

## Suggested validation sequence

1. **Baseline:** launch isolated profile, capture startup time, heap usage,
   video settings, selected shaderpack, loaded mod count and `latest.log`.
   Compare with the minimal development profile.
2. **World:** load a **copy** of the heavy map; traverse dense chunk regions,
   unload/reload chunks, check tile entities and custom geometry. Capture FPS,
   frame-time spikes, memory/GC and client freezes.
3. **Editor preview:** add/select Blockbuster, Emoticons and Chameleon actors
   while keeping the world loaded; change actor order, switch scenes and scrub.
4. **Effects:** add BetterLights lights and a mirror; test with shaders off
   and then with the user's shaderpack on; check screenshots, reflections
   and geometry/lighting of models.
5. **Animation/export:** play complex Record, test camera and media clips,
   physics simulation, then render a short controlled 1080p/60 fps clip.
   Compare rendered frame count and real playback duration.

Record per scenario: approximate FPS and 1% lows if available, RAM and VRAM,
chunk view distance, number of active actors, shaderpack, resolution,
log errors, and whether saved scene/world data remain intact.

The source modpack and original world are never modified by this tool.
Do not commit `run-stress` or distribute other creators' mod JARs via Git.
