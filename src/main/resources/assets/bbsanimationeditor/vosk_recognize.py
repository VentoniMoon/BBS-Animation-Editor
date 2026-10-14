# Bundled offline recognizer bridge for BBS Animation Editor.
# Dependency: python -m pip install vosk
import json
import sys
import wave
import audioop
from vosk import Model, KaldiRecognizer

audio_path, model_path = sys.argv[1:3]
with wave.open(audio_path, "rb") as audio:
    channels = audio.getnchannels()
    width = audio.getsampwidth()
    rate = audio.getframerate()
    if audio.getcomptype() != "NONE":
        raise ValueError("Use an uncompressed PCM WAV file")
    if width not in (1, 2, 3, 4):
        raise ValueError("Unsupported WAV sample width")
    model = Model(model_path)
    recognizer = KaldiRecognizer(model, 16000)
    recognizer.SetWords(True)
    rate_state = None

    def print_words(result):
        for word in json.loads(result).get("result", []):
            token = word.get("word", "").replace("\t", " ").replace("\n", " ")
            if token:
                print("%.5f\t%.5f\t%s" % (word["start"], word["end"], token), flush=True)

    while True:
        pcm = audio.readframes(4096)
        if not pcm:
            break
        if width != 2:
            pcm = audioop.lin2lin(pcm, width, 2)
        if channels != 1:
            # Stereo downmix; >2 channels are deliberately rejected.
            if channels != 2:
                raise ValueError("Only mono and stereo WAV supported")
            pcm = audioop.tomono(pcm, 2, 0.5, 0.5)
        if rate != 16000:
            pcm, rate_state = audioop.ratecv(pcm, 2, 1, rate, 16000, rate_state)
        if recognizer.AcceptWaveform(pcm):
            print_words(recognizer.Result())
    print_words(recognizer.FinalResult())
