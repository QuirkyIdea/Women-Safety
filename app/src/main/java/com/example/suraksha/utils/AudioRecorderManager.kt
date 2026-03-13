package com.example.suraksha.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * AudioRecorderManager — wraps [MediaRecorder] to record microphone audio
 * into the app's cache directory.
 *
 * Usage in the 60-second SOS loop:
 *  1. [startRecording] → begins a new .m4a clip.
 *  2. After 60 s the caller invokes [stopRecording] → returns the [File].
 *  3. File is sent via WhatsApp, then [deleteFile] cleans it up.
 */
class AudioRecorderManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorderManager"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    var isRecording = false
        private set

    /**
     * Begin recording to a new cache file.
     * @return the output [File], or null on failure.
     */
    fun startRecording(): File? {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return currentFile
        }
        if (!PermissionManager.hasAudioPermission(context)) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return null
        }

        try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            currentFile = File(context.cacheDir, "sos_audio_$ts.m4a")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(currentFile!!.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            Log.d(TAG, "Recording started → ${currentFile!!.absolutePath}")
            return currentFile
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed: ${e.message}")
            cleanup()
            return null
        }
    }

    /**
     * Stop the current recording.
     * @return the completed [File], or null if nothing was recording.
     */
    fun stopRecording(): File? {
        if (!isRecording) return null
        return try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            Log.d(TAG, "Recording stopped → ${currentFile?.absolutePath}")
            currentFile
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording failed: ${e.message}")
            cleanup()
            null
        }
    }

    /** Delete a previously recorded file from cache. */
    fun deleteFile(file: File?) {
        try {
            if (file != null && file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteFile failed: ${e.message}")
        }
    }

    /** Release the MediaRecorder (call in onDestroy). */
    fun release() {
        cleanup()
    }

    private fun cleanup() {
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
    }
}
