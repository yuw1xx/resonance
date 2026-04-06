package dev.yuwixx.resonance.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import dev.yuwixx.resonance.data.model.Song
import dev.yuwixx.resonance.data.model.WaveformData
import dev.yuwixx.resonance.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.math.pow
import kotlin.random.Random

@Composable
fun WaveformSeekbar(
    waveformData: WaveformData?,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    playedColor: Color = MaterialTheme.colorScheme.primary,
    remainingColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
    barWidthDp: Dp = 2.5.dp,
    barSpacingDp: Dp = 1.5.dp,
    minHeightFraction: Float = 0.12f,
) {
    val haptics = LocalHapticFeedback.current

    // Smooth progress interpolation
    val targetProgress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "waveform_progress",
    )

    // Breathing amplitude multiplier when playing — subtle, only ±4%
    val infiniteTransition = rememberInfiniteTransition(label = "wave_breathe")
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe_amp",
    )
    val ampMultiplier by animateFloatAsState(
        targetValue = if (isPlaying) breathe else 1f,
        animationSpec = tween(300),
        label = "amp_multiplier",
    )

    // Drag state for immediate visual feedback while scrubbing
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isDragging) dragProgress else animatedProgress

    val amplitudes = remember(waveformData) {
        // Fallback: synthesize a realistic-looking waveform from trig functions
        waveformData?.amplitudes ?: FloatArray(300) { i ->
            val t = i / 300f
            val base = abs(sin(t * PI.toFloat() * 14f)) * 0.5f
            val detail = abs(sin(t * PI.toFloat() * 43f)) * 0.25f
            val envelope = sin(t * PI.toFloat()).pow(0.4f)  // louder in middle
            (base + detail) * envelope + 0.08f
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(durationMs) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragProgress = fraction
                        if (change.pressed) {
                            isDragging = true
                            onSeek((fraction * durationMs).toLong())
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else {
                            isDragging = false
                        }
                    }
                }
            },
    ) {
        val barW  = barWidthDp.toPx()
        val gap   = barSpacingDp.toPx()
        val step  = barW + gap
        val count = (size.width / step).toInt().coerceAtLeast(1)
        val cx    = size.width / 2f
        val cy    = size.height / 2f
        val splitX = displayProgress * size.width

        for (i in 0 until count) {
            val barCX = i * step + barW / 2f

            // Map bar index → amplitude
            val ampIdx = (i.toFloat() / count * amplitudes.size).toInt()
                .coerceIn(0, amplitudes.lastIndex)
            val rawAmp = amplitudes[ampIdx].coerceAtLeast(minHeightFraction)

            // "Lift" effect — bars within a small window of the split are taller
            val distFromSplit = abs(barCX - splitX) / size.width
            val lift = if (distFromSplit < 0.04f) {
                val t = 1f - (distFromSplit / 0.04f)
                1f + t * t * 0.55f   // smooth quadratic peak at the split
            } else 1f

            val barH = (rawAmp * ampMultiplier * lift * size.height)
                .coerceIn(size.height * minHeightFraction, size.height * 0.95f)

            val top    = cy - barH / 2f
            val bottom = cy + barH / 2f
            val isPlayed = barCX <= splitX

            drawLine(
                color = if (isPlayed) playedColor else remainingColor,
                start = Offset(barCX, top),
                end   = Offset(barCX, bottom),
                strokeWidth = barW,
                cap   = StrokeCap.Round,
            )
        }
    }
}

// ─── Song Card (list item) ────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongCard(
    song: Song,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onLeadingClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isPlaying || isSelected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "song_scale",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { lc ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        lc()
                    }
                },
            ),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            isPlaying -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            else -> Color.Transparent
        },
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading area
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .let { base ->
                        if (onLeadingClick != null) {
                            base.clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLeadingClick()
                            }
                        } else base
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isSelected,
                    transitionSpec = {
                        fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f) togetherWith
                                fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f)
                    },
                    label = "leading_selection"
                ) { selected ->
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                null,
                                modifier = Modifier.align(Alignment.Center),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
                        ArtworkImage(
                            uri = song.artworkUri,
                            contentDescription = song.album,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 12.dp,
                            isAnimating = isPlaying,
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    color = if (isPlaying || isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (trailingContent != null) {
                trailingContent()
            } else if (isPlaying) {
                PlayingBarsIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ─── Animating Artwork Image ─────────────────────────────────────────────────

@Composable
fun ArtworkImage(
    uri: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    isAnimating: Boolean = false,
) {
    val breatheScale by animateFloatAsState(
        targetValue = if (isAnimating) 1.04f else 1f,
        animationSpec = if (isAnimating) {
            infiniteRepeatable(
                animation = tween(1500, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            )
        } else {
            tween(300)
        },
        label = "breathe_scale",
    )

    Box(
        modifier = modifier
            .scale(breatheScale)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

// ─── Playing Bars Indicator ───────────────────────────────────────────────────

@Composable
fun PlayingBarsIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 3,
) {
    val transitions = List(barCount) { i ->
        rememberInfiniteTransition(label = "bar_$i").animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400 + i * 100, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(i * 150),
            ),
            label = "bar_height_$i",
        )
    }

    Canvas(modifier = modifier) {
        val barW = size.width / (barCount * 2 - 1)
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f)
        transitions.forEachIndexed { i, anim ->
            val h = (size.height * anim.value).coerceAtLeast(barW) // never shorter than a dot
            drawRoundRect(
                color = color,
                topLeft = Offset(i * barW * 2f, (size.height - h) / 2f),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = cornerRadius,
            )
        }
    }
}

// ─── Particles Effect ─────────────────────────────────────────────────────────

@Composable
fun ParticlesEffect(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    audioPeak: Float = 0.5f,
    particleCount: Int = 30,
) {
    data class Particle(
        val x: Float, val y: Float, val radius: Float,
        val speedX: Float, val speedY: Float, val alpha: Float,
    )

    var particles by remember {
        mutableStateOf(
            List(particleCount) {
                Particle(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    radius = Random.nextFloat() * 4f + 1f,
                    speedX = (Random.nextFloat() - 0.5f) * 0.002f,
                    speedY = -(Random.nextFloat() * 0.003f + 0.001f),
                    alpha = Random.nextFloat() * 0.6f + 0.1f,
                )
            }
        )
    }

    val speedMultiplier = 1f + audioPeak * 3f

    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            particles = particles.map { p ->
                var nx = p.x + p.speedX * speedMultiplier
                var ny = p.y + p.speedY * speedMultiplier
                if (ny < 0f) ny = 1f
                if (nx < 0f) nx = 1f else if (nx > 1f) nx = 0f
                p.copy(x = nx, y = ny)
            }
        }
    }

    Canvas(modifier = modifier) {
        particles.forEach { p ->
            drawCircle(
                color = color.copy(alpha = p.alpha),
                radius = p.radius,
                center = Offset(p.x * size.width, p.y * size.height),
            )
        }
    }
}

// ─── Party Mode Edge Breathing Effect ─────────────────────────────────────────

@Composable
fun PartyModeEdge(
    modifier: Modifier = Modifier,
    colors: List<Color>,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "party")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Reverse),
        label = "party_phase",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = 8.dp.toPx() * (0.6f + 0.4f * phase)
        val colorIdx = (phase * (colors.size - 1)).toInt().coerceIn(0, colors.lastIndex)
        drawRect(
            color = colors[colorIdx].copy(alpha = 0.7f + 0.3f * phase),
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = androidx.compose.ui.geometry.Size(
                size.width - strokeWidth,
                size.height - strokeWidth,
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth),
        )
    }
}

// ─── Mini Player ─────────────────────────────────────────────────────────────

@Composable
fun MiniPlayer(
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val positionMs by playerViewModel.positionMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()
    val song = currentSong ?: return

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(
                    uri = song.artworkUri,
                    contentDescription = song.album,
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 8.dp,
                    isAnimating = isPlaying,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { playerViewModel.skipPrevious() }) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous")
                }
                // FIX: Animate play/pause icon in MiniPlayer the same way as PlayerScreen,
                // for visual consistency across the app.
                FilledIconButton(onClick = { playerViewModel.playPause() }) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.8f) togetherWith
                                    fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.8f)
                        },
                        label = "mini_play_pause"
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                        )
                    }
                }
                IconButton(onClick = { playerViewModel.skipNext() }) {
                    Icon(Icons.Rounded.SkipNext, "Next")
                }
            }
        }
    }
}

// ─── Utilities ────────────────────────────────────────────────────────────────

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

internal val EaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)