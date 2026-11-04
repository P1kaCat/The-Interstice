# tools/

Development-asset generators. They produce **temporary placeholder assets** only.

| Script | Output | Notes |
|---|---|---|
| `gen_textures.py` | block / entity / particle textures, icon | Procedural (numpy + Pillow). Overwrite with PixelLab art (see `docs/PIXELLAB_ASSETS.md`). |
| `gen_json.py` | blockstates, models, item models, particles, `sounds.json`, `lang/en_us.json` | Re-run after adding blocks/sounds. |
| `gen_audio.py` | all `.ogg` sounds (mono, 32 kHz, Vorbis) | Original DSP synthesis (numpy/scipy/soundfile). `--voices DIR` whisper-vocodes local TTS `.wav` renders (LPC noise vocoder). |

Run with an isolated interpreter from the project root:

```bash
python -I tools/gen_textures.py
```

```bash
python -I tools/gen_json.py
```

```bash
python -I tools/gen_audio.py --voices path/to/tts_wavs
```

Voice renders used for the current placeholders were produced offline with Windows SAPI
(`System.Speech`, voice "Microsoft Zira Desktop", rate -3), one `.wav` per line id
(`hide`, `dont_move`, `not_yet`, `go`, `they_hear`, `look_up`, `closer`, `stay`).
