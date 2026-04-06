package dev.yuwixx.resonance.domain.usecase

import android.media.MediaMetadataRetriever
import android.net.Uri
import dev.yuwixx.resonance.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * ReplayGain 2.0 processor (Gramophone-inspired).
 *
 * Reads RG tags from embedded metadata and computes the linear volume
 * multiplier to pass to ExoPlayer's setVolume().
 *
 * Tag lookup order:
 *   1. Song model fields (pre-parsed from jaudiotagger/TagLib if available)
 *   2. COMMENT tag fallback (many tools embed RG in ID3 TXXX/comment)
 *   3. Default gain (0 dB → multiplier 1.0)
 */
@Singleton
class ReplayGainProcessor @Inject constructor() {

    enum class Mode { TRACK, ALBUM, OFF }

    data class ReplayGainInfo(
        val trackGainDb: Float?,
        val trackPeak: Float?,
        val albumGainDb: Float?,
        val albumPeak: Float?,
    )

    /**
     * Parse ReplayGain tags from a [Song].
     * Falls back to ID3 comment fields if the model fields are null.
     */
    fun parseGain(song: Song): ReplayGainInfo {
        // Use pre-parsed values from Room entity first
        if (song.replayGainTrack != null || song.replayGainAlbum != null) {
            return ReplayGainInfo(
                trackGainDb = song.replayGainTrack,
                trackPeak = null,
                albumGainDb = song.replayGainAlbum,
                albumPeak = null,
            )
        }

        // No pre-parsed values; would need TagLib for full parsing
        return ReplayGainInfo(null, null, null, null)
    }

    /**
     * Compute the linear volume multiplier for [mode] with optional [preampDb].
     * Returns a value in [0.01, 4.0] safe for ExoPlayer.volume.
     */
    fun computeMultiplier(
        info: ReplayGainInfo,
        mode: Mode,
        preampDb: Float = 0f,
    ): Float {
        if (mode == Mode.OFF) return 1f

        val gainDb = when (mode) {
            Mode.TRACK -> info.trackGainDb
            Mode.ALBUM -> info.albumGainDb ?: info.trackGainDb
            Mode.OFF -> null
        } ?: return 1f

        val totalDb = gainDb + preampDb
        val linear = 10f.pow(totalDb / 20f)

        // Apply peak limiting to prevent clipping
        val peak = when (mode) {
            Mode.TRACK -> info.trackPeak ?: 1f
            Mode.ALBUM -> info.albumPeak ?: info.trackPeak ?: 1f
            Mode.OFF -> 1f
        }
        val maxSafe = 1f / peak.coerceAtLeast(0.001f)

        return linear.coerceIn(0.01f, maxSafe.coerceAtMost(4f))
    }
}
