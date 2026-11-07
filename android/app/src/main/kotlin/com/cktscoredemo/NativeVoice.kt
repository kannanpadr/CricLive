package com.cktscoredemo

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.webkit.JavascriptInterface
import java.util.Locale

/**
 * Spoken commentary using the phone's own Text-to-Speech engine.
 * Android WebView has no reliable speechSynthesis, so the web pages call this
 * through the "AndroidVoice" JavaScript bridge instead.
 */
object NativeVoice {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var failed = false
    private val pending = mutableListOf<Array<Any>>()
    private var lastText = ""
    private var lastAt = 0L

    fun init(context: Context) {
        if (tts != null || failed) return
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    synchronized(pending) {
                        pending.forEach { p -> speakNow(p[0] as String, p[1] as String, p[2] as Float, p[3] as Float, p[4] as Float) }
                        pending.clear()
                    }
                } else {
                    failed = true
                    Log.w("CktVoice", "Text-to-Speech init failed: $status")
                }
            }
        } catch (t: Throwable) {
            failed = true
            Log.w("CktVoice", "Text-to-Speech unavailable", t)
        }
    }

    fun speak(text: String, lang: String, rate: Float, pitch: Float, volume: Float) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        // Two pages (control panel + overlay) may ask for the same line; say it once
        val now = System.currentTimeMillis()
        if (clean == lastText && now - lastAt < 20_000) return
        lastText = clean
        lastAt = now
        if (!ready) {
            synchronized(pending) { if (pending.size < 3) pending.add(arrayOf(clean, lang, rate, pitch, volume)) }
            return
        }
        speakNow(clean, lang, rate, pitch, volume)
    }

    private fun speakNow(text: String, lang: String, rate: Float, pitch: Float, volume: Float) {
        val engine = tts ?: return
        try {
            val locale = Locale.forLanguageTag(lang.ifBlank { "en-IN" })
            val res = engine.setLanguage(locale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.ENGLISH)
            }
            engine.setSpeechRate(rate.coerceIn(0.5f, 2f))
            engine.setPitch(pitch.coerceIn(0.5f, 2f))
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
            engine.speak(text, TextToSpeech.QUEUE_ADD, params, "ckt-" + System.nanoTime())
        } catch (t: Throwable) {
            Log.w("CktVoice", "speak failed", t)
        }
    }

    fun stop() { try { tts?.stop() } catch (_: Throwable) {} }

    fun isSpeaking(): Boolean = try { tts?.isSpeaking == true } catch (_: Throwable) { false }

    fun isAvailable(): Boolean = !failed

    /** Bridge object exposed to the WebViews as window.AndroidVoice */
    class Bridge(private val context: Context) {
        init { init(context) }

        @JavascriptInterface
        fun speak(text: String?, lang: String?, rate: Float, pitch: Float, volume: Float) {
            NativeVoice.speak(text ?: "", lang ?: "en-IN", rate, pitch, volume)
        }

        @JavascriptInterface
        fun stop() = NativeVoice.stop()

        @JavascriptInterface
        fun isSpeaking(): Boolean = NativeVoice.isSpeaking()

        @JavascriptInterface
        fun isAvailable(): Boolean = NativeVoice.isAvailable()
    }
}
