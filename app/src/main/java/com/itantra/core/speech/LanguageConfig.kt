package com.itantra.core.speech

enum class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val ttsModelDir: String,
    val ttsModelFile: String,
    val sttLanguageCode: String // Whisper language code
) {
    ENGLISH("en", "English", "English", "models/tts/en", "en_US-amy-low.onnx", "en"),
    HINDI("hi", "Hindi", "हिन्दी", "models/tts/hi", "hi_IN-priyamvada-medium.onnx", "hi"),
    GUJARATI("gu", "Gujarati", "ગુજરાતી", "models/tts/gu", "gu_IN-gu-medium.onnx", "gu"),
    MARATHI("mr", "Marathi", "मराठी", "models/tts/mr", "mr_IN-mr-medium.onnx", "mr"),
    KANNADA("kn", "Kannada", "ಕನ್ನಡ", "models/tts/kn", "kn_IN-kn-medium.onnx", "kn"),
    MALAYALAM("ml", "Malayalam", "മലയാളം", "models/tts/ml", "ml_IN-ml-medium.onnx", "ml"),
    TAMIL("ta", "Tamil", "தமிழ்", "models/tts/ta", "ta_IN-ta-medium.onnx", "ta"),
    TELUGU("te", "Telugu", "తెలుగు", "models/tts/te", "te_IN-te-medium.onnx", "te"),
    ODIA("or", "Odia", "ଓଡ଼ିଆ", "models/tts/or", "or_IN-or-medium.onnx", "or"),
    BENGALI("bn", "Bengali", "বাংলা", "models/tts/bn", "bn_IN-bn-medium.onnx", "bn");
    
    companion object {
        fun fromCode(code: String): SupportedLanguage {
            return entries.find { it.code == code } ?: ENGLISH
        }
    }
}
