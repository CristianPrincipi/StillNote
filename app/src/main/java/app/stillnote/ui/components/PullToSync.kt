package app.stillnote.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.stillnote.ui.theme.StillColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Pull [content] down from its top to sync with Notion. The page stays still: a paper disc with
 * the refresh icon comes out from under the header, turning as it is pulled, and spins while
 * [syncing]. Pulling does nothing when [enabled] is false, as without Notion. [content] must
 * scroll vertically, even when it fits, or the pull never reaches here.
 */
@Composable
fun PullToSync(
    syncing: Boolean,
    enabled: Boolean,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val trigger = with(density) { Trigger.toPx() }
    val rest = with(density) { Rest.toPx() }
    val limit = with(density) { Limit.toPx() }
    val scope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnSync by rememberUpdatedState(onSync)

    /** How far the disc has come down, in px. */
    val pulled = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }

    // Once let go, the disc waits while the sync runs, then goes back up.
    LaunchedEffect(dragging, syncing) {
        if (!dragging) pulled.animateTo(if (syncing) rest else 0f)
    }
    LaunchedEffect(syncing) {
        if (syncing) while (true) spin.animateTo(spin.value + 360f, tween(SpinMillis, easing = LinearEasing))
    }

    val connection = remember(trigger, limit) {
        object : NestedScrollConnection {
            private var distance = 0f

            /** Moves the disc by a finger's [delta] and returns how much of it was used. */
            private fun pullBy(delta: Float): Float {
                if (!dragging) {
                    dragging = true
                    distance = pulled.value
                }
                val before = distance
                distance = (distance + delta * Resistance).coerceIn(0f, limit)
                scope.launch { pulled.snapTo(distance) }
                return (distance - before) / Resistance
            }

            // Pushing back up takes the pull away before the list scrolls.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (dragging && source == NestedScrollSource.UserInput && available.y < 0f) Offset(0f, pullBy(available.y))
                else Offset.Zero

            // Whatever the list can't scroll at its top becomes the pull.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (currentEnabled && source == NestedScrollSource.UserInput && available.y > 0f) Offset(0f, pullBy(available.y))
                else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!dragging) return Velocity.Zero
                dragging = false
                if (distance >= trigger) currentOnSync()
                return available
            }
        }
    }

    Box(modifier.clipToBounds().nestedScroll(connection)) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (pulled.value - DiscSize.toPx()).roundToInt()) }
                .graphicsLayer { alpha = (pulled.value / rest).coerceIn(0f, 1f) }
                .size(DiscSize)
                .paperShadow(CircleShape, Shadows.miniPostIt)
                .background(StillColors.card, CircleShape)
                .inkBorder(1.6.dp, StillColors.ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            InkIcon(
                StillIcons.Refresh,
                20.dp,
                Modifier.graphicsLayer { rotationZ = pulled.value / trigger * 270f + spin.value },
            )
        }
    }
}

private val DiscSize = 40.dp

/** Where the disc's bottom edge stops: far enough to sync, the resting place while syncing, and the most. */
private val Trigger = 80.dp
private val Rest = 64.dp
private val Limit = 128.dp

/** The disc moves half as far as the finger. */
private const val Resistance = 0.5f
private const val SpinMillis = 900
