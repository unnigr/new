package com.unni.simplemusicplayer

import android.media.audiofx.Equalizer
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.roundToInt

object PlaybackAudio {
    var bass: Float = 0f
    var treble: Float = 0f
    private var eq: Equalizer? = null

    fun attach(player: ExoPlayer) {
        try {
            eq?.release()
            eq = Equalizer(0, player.audioSessionId).apply {
                enabled = true
                // Use the lowest and highest available bands as simple Bass/Treble controls.
                if (numberOfBands >= 2) {
                    setBandLevel(0.toShort(), 0)
                    setBandLevel((numberOfBands - 1).toShort(), 0)
                }
            }
        } catch (_: Exception) { eq = null }
    }

    fun setBass(value: Float) {
        bass=value
        apply()
    }

    fun setTreble(value: Float) {
        treble=value
        apply()
    }

    private fun apply() {
        try {
            val e=eq ?: return
            val min=e.bandLevelRange[0].toInt()
            val max=e.bandLevelRange[1].toInt()
            val bassLevel=(bass/12f*(max.coerceAtLeast(kotlin.math.abs(min)))).roundToInt().coerceIn(min,max)
            val trebleLevel=(treble/12f*(max.coerceAtLeast(kotlin.math.abs(min)))).roundToInt().coerceIn(min,max)
            if(e.numberOfBands>=2) {
                e.setBandLevel(0.toShort(), bassLevel.toShort())
                e.setBandLevel((e.numberOfBands-1).toShort(), trebleLevel.toShort())
            }
        } catch (_: Exception) {}
    }
}
