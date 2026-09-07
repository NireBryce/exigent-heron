package net.breadthcharge.exigentheron.speech

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager

/**
 * Registers for [AudioManager.ACTION_AUDIO_BECOMING_NOISY] — Android's own
 * signal that the active output route is about to fall back to a less
 * private one *while something is already playing* (headphones pulled
 * mid-utterance, a Bluetooth device dropping mid-utterance). This is a
 * different gap than [OutputRouteGate]/[SpeechQueue]'s per-item
 * `isOutputRouteAllowed` re-check: that one only ever sees the route
 * *between* utterances, never a change during one already in flight.
 *
 * [onBecomingNoisy] is wired by `AppContainer` to [SpeechQueue.stopCurrent]
 * — the standard place apps stop/pause playback on this broadcast, same
 * reasoning `MediaPlayer`-based apps use it for.
 *
 * Registered once for the process lifetime (`AppContainer` is a
 * singleton; there is no matching `unregister` because there is nothing
 * that ever tears this down before the process dies) — a leaked
 * receiver would matter for a short-lived component, not for something
 * that already lives as long as the app does.
 */
class AudioBecomingNoisyReceiver(context: Context, onBecomingNoisy: () -> Unit) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onBecomingNoisy()
        }
    }

    init {
        context.applicationContext.registerReceiver(receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }
}
