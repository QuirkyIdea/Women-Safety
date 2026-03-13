package com.example.suraksha.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.*
import java.util.*

/**
 * VoiceTriggerManager — continuously listens for a custom voice phrase
 * and fires [onTrigger] when matched.
 *
 * Designed to remain active even after SOS stops (per requirements).
 * Uses Android's [SpeechRecognizer]; automatically restarts listening
 * after each recognition cycle or error.
 */
class VoiceTriggerManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceTriggerManager"
        private const val RESTART_DELAY = 1500L
    }

    private var recognizer: SpeechRecognizer? = null
    private var command = "help me"
    private var onTrigger: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var listening = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Public API ──────────────────────────────────────────────────────

    fun setCommand(phrase: String) {
        command = phrase.lowercase(Locale.getDefault()).trim()
    }

    fun getCommand(): String = command

    fun setOnTriggerListener(listener: () -> Unit) { onTrigger = listener }
    fun setOnErrorListener(listener: (String) -> Unit) { onError = listener }

    fun startListening() {
        if (listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition unavailable")
            onError?.invoke("Speech recognition not available")
            return
        }
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(listener)
            begin()
            listening = true
            Log.d(TAG, "Voice trigger listening for \"$command\"")
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed: ${e.message}")
        }
    }

    fun stopListening() {
        listening = false
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
            recognizer = null
        } catch (_: Exception) {}
    }

    fun isActive(): Boolean = listening

    fun destroy() {
        stopListening()
        scope.cancel()
    }

    // ── Internal ────────────────────────────────────────────────────────

    private fun begin() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        try { recognizer?.startListening(intent) } catch (e: Exception) {
            Log.e(TAG, "begin() failed: ${e.message}")
        }
    }

    private fun restart() {
        if (!listening) return
        scope.launch {
            delay(RESTART_DELAY)
            if (listening) begin()
        }
    }

    private fun matches(text: String): Boolean =
        text.lowercase(Locale.getDefault()).contains(command)

    // ── RecognitionListener ─────────────────────────────────────────────

    private val listener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.forEach {
                if (matches(it)) {
                    Log.d(TAG, "Voice command MATCHED: \"$it\"")
                    onTrigger?.invoke()
                    return@forEach
                }
            }
            restart()
        }

        override fun onPartialResults(partial: Bundle?) {
            partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.forEach {
                if (matches(it)) {
                    Log.d(TAG, "Partial match: \"$it\"")
                    onTrigger?.invoke()
                }
            }
        }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout"
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy"
                else -> "Error $error"
            }
            Log.d(TAG, "Recognition error: $msg")
            if (error != SpeechRecognizer.ERROR_CLIENT) restart()
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
