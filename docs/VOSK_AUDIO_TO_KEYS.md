# Audio to Keys (Vosk) — first offline prototype

The recognition stage uses Wemppy's approach: Vosk returns word timestamps; Java distributes visemes across each word and writes Body Parts Skin keys using the existing Text Lip Sync baker. The Vosk model itself is **not** bundled.

Setup for Minecraft 1.12.2 on Windows:

1. Install Python **3.10, 3.11 or 3.12** (the bundled WAV conversion script uses Python's `audioop`, removed in Python 3.13).
2. In PowerShell: `python -m pip install vosk`.
3. Download the Russian Vosk model listed as `vosk-model-small-ru-0.22` on https://alphacephei.com/vosk/models and unzip its **contents** so that the path is `<Minecraft game directory>/config/bbs-editor/vosk_models/model-small-ru/am` (also usually `conf`, `graph`, `ivector`). This is a separately downloaded model, not a custom model authored by Wemppy.
4. Put uncompressed PCM WAV into `<Minecraft game directory>/config/bbs-editor/audio`, and load it with `LOAD WAV`.
5. Select a Mouth Body Parts row in Character Timeline, open LIP SYNC → AUDIO TO KEYS, set a frame offset and idle texture, and click Analyze + Generate.

The speech processing is fully offline. The external Python process runs on a worker thread; all Character key mutations run back in the editor's GUI update loop. Existing manual Skin keys are protected from overwriting. If the generated keys exceed the Body Part's length, generation aborts: extend the scene or use a shorter audio clip. The recognition model produces approximate **word** timing, not exact phoneme alignment. Lip positions will need manual adjustment for singing.

For the first test use a short Russian WAV and an empty section of Character Timeline. The initial `test.wav` sample is ~35 seconds and requires a sufficiently long actor/Body Part track. Python must be available as `python` on PATH or configured in the Audio-to-Keys dialog.
