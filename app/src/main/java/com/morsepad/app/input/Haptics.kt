package com.morsepad.app.input

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import com.morsepad.core.session.SessionUpdate
import java.util.concurrent.Executors

/**
 * Tactile feedback for chords.
 *
 * Why not `View.performHapticFeedback`: view haptics obey the system "touch feedback" switch
 * and, since Android 15, the separate "keyboard vibration" switch — with either one off they
 * silently do nothing, which made the in-app setting look broken. A plain [Vibrator] call is
 * not enough either: Android classifies short effects without attributes as touch feedback
 * (`usage: TOUCH`) and drops them the same way (`ignored_for_settings`).
 *
 * So every vibration declares its purpose explicitly: on Android 13+ as
 * `USAGE_PHYSICAL_EMULATION` (emulated key presses — exactly what the chord keys are), whose
 * intensity is independent of the touch-feedback switch; before that as media-class vibration.
 * Only the in-app switch ([enabled]) and the device's master vibration setting apply.
 *
 * Vibrator calls are binder transactions, so they run on a dedicated thread and never delay
 * the touch handler that commits the character.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "haptics").apply { isDaemon = true }
    }

    private val tick: VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneShot(TICK_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
        }

    private val reject: VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, TICK_MILLIS, 60, TICK_MILLIS), -1)
        }

    /** Mirrors the in-app "vibration" setting. */
    @Volatile
    var enabled: Boolean = true

    val isAvailable: Boolean get() = vibrator?.hasVibrator() == true

    /** Feedback for one input action: a tick for a recognised chord, a double pulse for a dropped prefix. */
    fun after(update: SessionUpdate) {
        when {
            update.prefixCancelled -> play(reject)
            update.committedChar != null || update.textChanged || update.prefixPending -> play(tick)
        }
    }

    /** A single tick, e.g. for a key press. */
    fun tick() = play(tick)

    /** Plays a tick even while disabled — used to preview the setting when it is switched on. */
    fun preview() = play(tick, force = true)

    private fun play(effect: VibrationEffect, force: Boolean = false) {
        if (!enabled && !force) return
        val target = vibrator ?: return
        worker.execute {
            if (!target.hasVibrator()) return@execute
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                target.vibrate(effect, KeyPressAttributes.value)
            } else {
                @Suppress("DEPRECATION")
                target.vibrate(effect, LEGACY_ATTRIBUTES)
            }
        }
    }

    private companion object {
        const val TICK_MILLIS = 15L

        val LEGACY_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}

/** Loaded only on Android 13+, where VibrationAttributes and this usage exist. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object KeyPressAttributes {
    val value: VibrationAttributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_PHYSICAL_EMULATION)
}
