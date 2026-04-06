package dev.yuwixx.resonance.ui.glancewidget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.*
import dev.yuwixx.resonance.MainActivity
import dev.yuwixx.resonance.ui.theme.DarkColorScheme
import dev.yuwixx.resonance.ui.theme.LightColorScheme

// ─── Broadcast action constants (consumed by MusicService) ────────────────────
const val ACTION_WIDGET_PLAY_PAUSE = "dev.yuwixx.resonance.WIDGET_PLAY_PAUSE"
const val ACTION_WIDGET_SKIP_NEXT  = "dev.yuwixx.resonance.WIDGET_SKIP_NEXT"

// ─── Preferences keys shared with MusicService ────────────────────────────────
val KEY_TITLE      = stringPreferencesKey("widget_title")
val KEY_ARTIST     = stringPreferencesKey("widget_artist")
val KEY_IS_PLAYING = booleanPreferencesKey("widget_is_playing")
val KEY_HAS_SONG   = booleanPreferencesKey("widget_has_song")

class ResonanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(
                colors = ColorProviders(
                    light = LightColorScheme,
                    dark = DarkColorScheme,
                )
            ) {
                val prefs     = currentState<Preferences>()
                val hasSong   = prefs[KEY_HAS_SONG]   ?: false
                val title     = prefs[KEY_TITLE]       ?: ""
                val artist    = prefs[KEY_ARTIST]      ?: ""
                val isPlaying = prefs[KEY_IS_PLAYING]  ?: false

                ResonanceWidgetContent(
                    hasSong   = hasSong,
                    title     = title,
                    artist    = artist,
                    isPlaying = isPlaying,
                )
            }
        }
    }

    companion object {
        /** Called from MusicService to push new state and trigger a redraw. */
        suspend fun updateState(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            hasSong: Boolean,
        ) {
            val glanceIds = GlanceAppWidgetManager(context)
                .getGlanceIds(ResonanceWidget::class.java)

            for (id in glanceIds) {
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[KEY_TITLE]      = title
                        this[KEY_ARTIST]     = artist
                        this[KEY_IS_PLAYING] = isPlaying
                        this[KEY_HAS_SONG]   = hasSong
                    }
                }
                ResonanceWidget().update(context, id)
            }
        }
    }
}

@Composable
private fun ResonanceWidgetContent(
    hasSong: Boolean,
    title: String,
    artist: String,
    isPlaying: Boolean,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        if (!hasSong) {
            // ── Idle state ────────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Resonance",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    ),
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = "Nothing playing",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }
        } else {
            // ── Now-playing state ─────────────────────────────────────────────
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // App label
                Text(
                    text = "RESONANCE",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                    ),
                )

                Spacer(GlanceModifier.height(6.dp))

                // Song title
                Text(
                    text = title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                    maxLines = 1,
                )

                Spacer(GlanceModifier.height(2.dp))

                // Artist name
                Text(
                    text = artist,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                )

                Spacer(GlanceModifier.height(10.dp))

                // Control row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Play / Pause
                    Box(
                        modifier = GlanceModifier
                            .size(40.dp)
                            .background(GlanceTheme.colors.primaryContainer)
                            .cornerRadius(20.dp)
                            .clickable(
                                actionSendBroadcast(
                                    Intent(ACTION_WIDGET_PLAY_PAUSE)
                                        .setPackage("dev.yuwixx.resonance")
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isPlaying) "⏸" else "▶",
                            style = TextStyle(
                                color = GlanceTheme.colors.onPrimaryContainer,
                                fontSize = 16.sp,
                            ),
                        )
                    }

                    Spacer(GlanceModifier.width(10.dp))

                    // Skip next
                    Box(
                        modifier = GlanceModifier
                            .size(40.dp)
                            .background(GlanceTheme.colors.secondaryContainer)
                            .cornerRadius(20.dp)
                            .clickable(
                                actionSendBroadcast(
                                    Intent(ACTION_WIDGET_SKIP_NEXT)
                                        .setPackage("dev.yuwixx.resonance")
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "⏭",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSecondaryContainer,
                                fontSize = 16.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

class ResonanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ResonanceWidget()
}