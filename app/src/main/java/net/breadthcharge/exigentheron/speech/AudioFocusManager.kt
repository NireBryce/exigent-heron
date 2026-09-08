package net.breadthcharge.exigentheron.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Requests and abandons `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`.
 * [SpeechQueue] calls this once per burst (before the first item, after the queue drains),
 * not per-utterance — per-utterance causes audible ducking thrash on notification bursts.
 *
 * `USAGE_ASSISTANCE_ACCESSIBILITY`: this app reads notification content
 * aloud in place of the user looking at the screen, the same category
 * Android's own accessibility/screen-reader guidance uses.
 */
class AudioFocusManager(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var activeRequest: AudioFocusRequest? = null

    fun requestFocus(): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        activeRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandonFocus() {
        activeRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        activeRequest = null
    }
}
