package dev.yuwixx.resonance.domain.usecase

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yuwixx.resonance.data.model.WaveformData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Extracts waveform amplitude data from an audio file using MediaCodec.
 * Results are cached in-memory by song ID.
 *
 * Namida-style waveform: used by WaveformSeekbar for visual scrubbing.
 */
@Singleton
class WaveformExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = HashMap<Long, WaveformData>(64)

    suspend fun extract(songId: Long, uri: Uri, resolution: Int = 200): WaveformData? =
        withContext(Dispatchers.Default) {
            cache[songId]?.let { return@withContext it }

            try {
                val extractor = MediaExtractor()
                extractor.setDataSource(context, uri, null)

                // Find audio track
                var audioTrack = -1
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        audioTrack = i
                        break
                    }
                }
                if (audioTrack == -1) return@withContext null

                extractor.selectTrack(audioTrack)
                val format = extractor.getTrackFormat(audioTrack)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
                val totalUs = format.getLong(MediaFormat.KEY_DURATION)
                val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                val codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val amplitudes = FloatArray(resolution)
                val samplesPerBucket = ((totalUs / 1_000_000f) * sampleRate / resolution).toLong()
                    .coerceAtLeast(1L)

                var currentBucket = 0
                var sampleSum = 0.0
                var sampleCount = 0L
                var inputDone = false
                var outputDone = false
                val bufferInfo = MediaCodec.BufferInfo()

                while (!outputDone && currentBucket < resolution) {
                    // Feed input
                    if (!inputDone) {
                        val inputIdx = codec.dequeueInputBuffer(0)
                        if (inputIdx >= 0) {
                            val buf = codec.getInputBuffer(inputIdx)!!
                            val sampleSize = extractor.readSampleData(buf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIdx, 0, 0,
                                    0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIdx, 0, sampleSize,
                                    extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    // Drain output
                    val outputIdx = codec.dequeueOutputBuffer(bufferInfo, 0)
                    if (outputIdx >= 0) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                        val outBuf = codec.getOutputBuffer(outputIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.order(ByteOrder.nativeOrder())
                            val shortBuf = outBuf.asShortBuffer()
                            val frameCount = shortBuf.remaining() / channelCount
                            for (f in 0 until frameCount) {
                                var sample = 0L
                                for (c in 0 until channelCount) {
                                    sample += abs(shortBuf.get().toLong())
                                }
                                sample /= channelCount
                                sampleSum += sample.toDouble()
                                sampleCount++

                                if (sampleCount >= samplesPerBucket) {
                                    if (currentBucket < resolution) {
                                        amplitudes[currentBucket] =
                                            (sampleSum / sampleCount / Short.MAX_VALUE).toFloat()
                                                .coerceIn(0f, 1f)
                                        currentBucket++
                                    }
                                    sampleSum = 0.0
                                    sampleCount = 0L
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputIdx, false)
                    }
                }

                codec.stop()
                codec.release()
                extractor.release()

                // Fill remaining buckets with zeros and normalize
                val max = amplitudes.max().coerceAtLeast(0.001f)
                for (i in amplitudes.indices) {
                    amplitudes[i] = (amplitudes[i] / max).coerceIn(0f, 1f)
                }

                val result = WaveformData(amplitudes = amplitudes, resolution = resolution)
                cache[songId] = result
                result
            } catch (e: Exception) {
                null
            }
        }
}
