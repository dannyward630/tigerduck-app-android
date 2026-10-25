// A modal sheet that moves itself between half height and full screen, as iOS's medium and large
// detents do: softer and slower than Material3's sheet, and quicker to change height.
//
// TODO(#502): Material3 1.4.0's ModalBottomSheet settles with a spring an app can't change (its
//  MotionScheme is internal), so this sheet keeps ModalBottomSheet only as its window and moves
//  itself. Material3 1.5.0 makes the motion scheme public; once 1.5.0 is stable, re-evaluate going
//  back to Material's sheet with a softer scheme.

package org.ntust.app.tigerduck.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt
import kotlinx.coroutines.launch
import org.ntust.app.tigerduck.R

/** The heights a [DetentSheet] rests at. */
internal enum class DetentSheetValue { Hidden, Half, Full }

/** How a [DetentSheet] moves. */
internal object DetentSheetMotion {
    /**
     * Material3's sheet settles with damping 0.9 and stiffness 700: quick, and with a fling behind
     * it, past its top and back. This one is softer and takes about twice as long, and it is
     * critically damped, so it comes to rest without swinging past where it stops.
     */
    const val STIFFNESS = 200f

    val Spec: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = STIFFNESS)

    /** A fling at least this fast moves the sheet on to the next height. Material's is 125 dp/s. */
    val VelocityThreshold: Dp = 80.dp

    /** Let go at least this far from the last height it passed, the sheet goes on to the next. Material's is 56 dp. */
    val PositionalThreshold: Dp = 32.dp

    /** The spring's natural frequency, in radians a second. */
    private val omega = sqrt(STIFFNESS)

    /**
     * [velocity], held to what the spring can start from toward a height [distance] away without
     * passing it. A critically damped spring passes its target only when it starts toward it faster
     * than its natural frequency times the distance; a fling the other way never does.
     */
    fun startVelocity(velocity: Float, distance: Float): Float {
        if (velocity == 0f || distance == 0f || sign(velocity) != sign(distance)) return velocity
        val limit = omega * abs(distance)
        return velocity.coerceIn(-limit, limit)
    }
}

/** Where a [DetentSheet] is and how it moves there. Offsets are from the top of the window, in px. */
@Stable
internal class DetentSheetState(initialValue: DetentSheetValue, private val density: Density) {

    val draggable = AnchoredDraggableState(initialValue)

    /** The height the sheet last came to rest at. */
    val currentValue: DetentSheetValue get() = draggable.settledValue

    /** The height the sheet is on its way to, or rests at. */
    val targetValue: DetentSheetValue get() = draggable.targetValue

    /** Whether the sheet has come up since it was made; until then, resting out of sight isn't closing. */
    internal var hasShown = initialValue != DetentSheetValue.Hidden
        private set

    /** The window's height, once laid out: out of sight there, half way at half, the top at full. */
    fun updateHeight(height: Int) {
        val h = height.toFloat()
        draggable.updateAnchors(
            DraggableAnchors {
                DetentSheetValue.Hidden at h
                DetentSheetValue.Half at h / 2f
                DetentSheetValue.Full at 0f
            },
        )
    }

    /** Where the sheet's top is drawn: never above its full height, out of sight until laid out. */
    fun visibleOffset(): Int {
        val offset = draggable.offset
        if (offset.isNaN()) return OUT_OF_SIGHT
        return offset.coerceAtLeast(draggable.anchors.minPosition()).roundToInt()
    }

    /** The height a sheet let go at [offset], moving at [velocity], goes on to. */
    internal fun targetFor(offset: Float, velocity: Float): DetentSheetValue {
        val anchors = draggable.anchors
        val velocityThreshold = with(density) { DetentSheetMotion.VelocityThreshold.toPx() }
        if (abs(velocity) >= velocityThreshold) {
            return anchors.closestAnchor(offset, searchUpwards = velocity > 0f) ?: currentValue
        }
        val rested = anchors.positionOf(currentValue)
        if (rested.isNaN() || offset == rested) return anchors.closestAnchor(offset) ?: currentValue
        val down = offset > rested
        // The last height the sheet passed on its way here, and the next one ahead of it.
        val passed = anchors.closestAnchor(offset, searchUpwards = !down) ?: currentValue
        val ahead = anchors.closestAnchor(offset, searchUpwards = down) ?: currentValue
        val positionalThreshold = with(density) { DetentSheetMotion.PositionalThreshold.toPx() }
        return if (abs(offset - anchors.positionOf(passed)) >= positionalThreshold) ahead else passed
    }

    /** Settles a sheet let go moving at [velocity] at the height it goes on to. */
    suspend fun settle(velocity: Float) {
        val offset = draggable.offset
        if (offset.isNaN()) return
        animateTo(targetFor(offset, velocity), velocity)
    }

    /**
     * Moves the sheet to [value], starting at [velocity]. A sheet already there doesn't animate:
     * while it animates, it takes the next touch for its own, which a sheet at rest mustn't.
     */
    suspend fun animateTo(value: DetentSheetValue, velocity: Float = 0f) {
        val to = draggable.anchors.positionOf(value)
        if (to.isNaN() || draggable.offset.isNaN()) return
        if (abs(to - draggable.offset) < 0.5f) {
            if (draggable.settledValue != value) draggable.snapTo(value)
        } else {
            draggable.anchoredDrag(value) { anchors, latest ->
                val from = draggable.offset
                val end = anchors.positionOf(latest)
                animate(from, end, DetentSheetMotion.startVelocity(velocity, end - from), DetentSheetMotion.Spec) { value, speed ->
                    dragTo(value, speed)
                }
            }
        }
        if (draggable.settledValue != DetentSheetValue.Hidden) hasShown = true
    }

    /** A drag on the sheet itself, let go: on to the next height as [settle] picks it. */
    val flingBehavior = object : FlingBehavior {
        override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
            val from = draggable.offset
            if (from.isNaN()) return initialVelocity
            val end = draggable.anchors.positionOf(targetFor(from, initialVelocity))
            var previous = from
            animate(from, end, DetentSheetMotion.startVelocity(initialVelocity, end - from), DetentSheetMotion.Spec) { value, _ ->
                scrollBy(value - previous)
                previous = value
            }
            // Onto the height itself: each step is held inside the heights, so one that went past
            // an end would leave the sheet short of it.
            scrollBy(end - draggable.offset)
            if (draggable.offset < draggable.anchors.maxPosition() - 0.5f) hasShown = true
            return 0f
        }
    }

    /**
     * Between the sheet and its scrolling content, as Material's sheet does it: an upward drag
     * raises a sheet below full height before the content scrolls, and what the content leaves of a
     * drag or a fling moves the sheet.
     */
    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f && source == NestedScrollSource.UserInput && !draggable.offset.isNaN()) {
                Offset(0f, draggable.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && !draggable.offset.isNaN()) {
                Offset(0f, draggable.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val offset = draggable.offset
            return if (available.y < 0f && !offset.isNaN() && offset > draggable.anchors.minPosition()) {
                settle(available.y)
                available
            } else {
                Velocity.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle(available.y)
            return available
        }
    }

    companion object {
        private const val OUT_OF_SIGHT = Int.MAX_VALUE / 2

        fun Saver(density: Density) = Saver<DetentSheetState, DetentSheetValue>(
            save = { it.currentValue },
            restore = { DetentSheetState(it, density) },
        )
    }
}

/** A [DetentSheetState] that comes back as it was after the activity is recreated, as for a change of dark mode. */
@Composable
internal fun rememberDetentSheetState(): DetentSheetState {
    val density = LocalDensity.current
    return rememberSaveable(saver = DetentSheetState.Saver(density)) {
        DetentSheetState(DetentSheetValue.Hidden, density)
    }
}

/**
 * A modal sheet that rises to [openAt] and moves between half height and full screen. Dragged or
 * flung below half height, tapped outside or backed out of, it slides away and calls [onDismissed].
 *
 * Material's [ModalBottomSheet] is only its window here: full screen, see-through, its own gestures,
 * scrim and back handling off. The sheet inside moves itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetentSheet(
    state: DetentSheetState,
    openAt: DetentSheetValue,
    onDismissed: () -> Unit,
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Up from the start and never moved: it only holds the window.
    val window = remember {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { 0f },
            velocityThreshold = { 0f },
            initialValue = SheetValue.Expanded,
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismissed,
        sheetState = window,
        sheetMaxWidth = Dp.Unspecified,
        sheetGesturesEnabled = false,
        shape = RectangleShape,
        containerColor = Color.Transparent,
        // Sets the status and navigation bar icons the way the sheet's own color calls for.
        contentColor = contentColorFor(containerColor),
        scrimColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        DetentSheetLayer(state, openAt, onDismissed, containerColor, content = content)
    }
}

/** The scrim and the sheet, filling the window they're given. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetentSheetLayer(
    state: DetentSheetState,
    openAt: DetentSheetValue,
    onDismissed: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dismissed by rememberUpdatedState(onDismissed)
    val close: () -> Unit = { scope.launch { state.animateTo(DetentSheetValue.Hidden) } }
    val open by rememberUpdatedState(openAt)

    // Rises once laid out; one that comes back with the activity is already up.
    LaunchedEffect(state) {
        snapshotFlow { state.draggable.anchors.size }.collect { anchors ->
            if (anchors > 0 && state.currentValue == DetentSheetValue.Hidden && !state.hasShown) {
                state.animateTo(open)
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.currentValue }.collect {
            if (it == DetentSheetValue.Hidden && state.hasShown) dismissed()
        }
    }
    BackHandler(enabled = state.currentValue != DetentSheetValue.Hidden) {
        if (state.currentValue == DetentSheetValue.Full) {
            scope.launch { state.animateTo(DetentSheetValue.Half) }
        } else {
            close()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // The heights follow the window's, set before the sheet is placed so its first frame is right.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    state.updateHeight(placeable.height)
                    placeable.place(0, 0)
                }
            },
    ) {
        Scrim(visible = state.targetValue != DetentSheetValue.Hidden, onClose = close)
        val statusBarTop = WindowInsets.statusBars.getTop(LocalDensity.current)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = BottomSheetDefaults.SheetMaxWidth)
                .fillMaxSize()
                .offset { IntOffset(0, state.visibleOffset()) }
                .nestedScroll(state.nestedScroll)
                .anchoredDraggable(state.draggable, Orientation.Vertical, flingBehavior = state.flingBehavior)
                .clip(BottomSheetDefaults.ExpandedShape)
                .background(containerColor)
                .belowStatusBar(state, statusBarTop)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            Handle(state, onClose = close)
            content()
        }
    }
}

/**
 * Pads the sheet's top by however much of the status bar it is under, as Material's sheet does: none
 * at half height, all of it at full. Read while laying out, so a moving sheet doesn't recompose.
 */
private fun Modifier.belowStatusBar(state: DetentSheetState, statusBarTop: Int): Modifier =
    layout { measurable, constraints ->
        val offset = state.draggable.offset
        val top = if (offset.isNaN()) 0 else (statusBarTop - offset.roundToInt()).coerceIn(0, statusBarTop)
        val placeable = measurable.measure(constraints.copy(
            minHeight = (constraints.minHeight - top).coerceAtLeast(0),
            maxHeight = (constraints.maxHeight - top).coerceAtLeast(0),
        ))
        layout(placeable.width, placeable.height + top) { placeable.place(0, top) }
    }

/** Material's scrim: dims what's under the sheet, and a tap on it closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Scrim(visible: Boolean, onClose: () -> Unit) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "scrim")
    val closeLabel = stringResource(R.string.action_close)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(BottomSheetDefaults.ScrimColor)
            .then(
                if (visible) {
                    Modifier
                        .pointerInput(onClose) { detectTapGestures { onClose() } }
                        .semantics(mergeDescendants = true) {
                            traversalIndex = 1f
                            contentDescription = closeLabel
                            onClick { onClose(); true }
                        }
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * Material's drag handle, with what Material's sheet gives it: a tap raises a sheet at half height
 * and closes one at full, and TalkBack offers the same as expand, collapse and dismiss. Its name is
 * Material's own, already translated; the actions take TalkBack's own names.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Handle(state: DetentSheetState, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val expand = { scope.launch { state.animateTo(DetentSheetValue.Full) }; Unit }
    val collapse = { scope.launch { state.animateTo(DetentSheetValue.Half) }; Unit }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clickable {
                    when (state.currentValue) {
                        DetentSheetValue.Half -> expand()
                        DetentSheetValue.Full -> onClose()
                        DetentSheetValue.Hidden -> Unit
                    }
                }
                .semantics(mergeDescendants = true) {
                    dismiss { onClose(); true }
                    when (state.currentValue) {
                        DetentSheetValue.Half -> expand { expand(); true }
                        DetentSheetValue.Full -> collapse { collapse(); true }
                        DetentSheetValue.Hidden -> Unit
                    }
                },
        ) {
            BottomSheetDefaults.DragHandle()
        }
    }
}
