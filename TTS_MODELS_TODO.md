# ⚠️ ACTION REQUIRED: Download TTS Models

To complete the 10-language TTS support, you need to manually download the binary ONNX models for the newly added Indian languages.

## Download Instructions

1. **Visit the official repository:**
   Go to the [Sherpa-ONNX TTS Models (GitHub Releases)](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) page.

2. **Search for the Indian language Piper models:**
   Look for the `.tar.bz2` files prefixed with `vits-piper-<lang>_IN`. You need to download the following:
   * Gujarati: `vits-piper-gu_IN-...`
   * Marathi: `vits-piper-mr_IN-...`
   * Kannada: `vits-piper-kn_IN-...`
   * Malayalam: `vits-piper-ml_IN-...`
   * Tamil: `vits-piper-ta_IN-...`
   * Telugu: `vits-piper-te_IN-...`
   * Odia: `vits-piper-or_IN-...`
   * Bengali: `vits-piper-bn_IN-...`

3. **Extract the models:**
   Extract the contents of each `.tar.bz2` archive into their respective directories located at:
   `app/src/main/assets/models/tts/<lang>/`
   
   *Ensure each directory contains the `.onnx` file, the `.onnx.json` file, and `tokens.txt`.*

4. **Update `LanguageConfig.kt`:**
   Because the exact speaker/voice names (e.g. `nirant`, `priyamvada`) vary per language, open `app/src/main/java/com/itantra/core/speech/LanguageConfig.kt` and update the `ttsModelFile` parameter for each language to exactly match the name of the `.onnx` file you downloaded.

*Note: Without these models, selecting one of the new languages in the app settings will cause the TTS engine to safely fall back to English.*
