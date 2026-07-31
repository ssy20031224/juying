package com.juying.app.ui

/**
 * Pure interaction decisions shared by the player screen and gesture layer.
 * Keeping these free of Compose/Android state makes the edge cases testable.
 */
internal object PlayerInteractionPolicy {
    enum class DragAxis {
        UNDECIDED,
        VERTICAL,
        HORIZONTAL
    }

    fun showLandscapeSidePanel(isLandscape: Boolean, explicitFullscreen: Boolean): Boolean =
        isLandscape && !explicitFullscreen

    fun canStartHoldGesture(
        longPressDetected: Boolean,
        controlsLocked: Boolean,
        dragGestureActive: Boolean
    ): Boolean = longPressDetected && !controlsLocked && !dragGestureActive

    fun canUseLocalLibrary(
        accountFeaturesDisabled: Boolean,
        accountAvailable: Boolean
    ): Boolean = accountFeaturesDisabled || accountAvailable

    fun lockDragAxis(
        current: DragAxis,
        totalX: Float,
        totalY: Float,
        thresholdPx: Float = 12f
    ): DragAxis {
        if (current != DragAxis.UNDECIDED) return current
        if (kotlin.math.max(kotlin.math.abs(totalX), kotlin.math.abs(totalY)) < thresholdPx) {
            return DragAxis.UNDECIDED
        }
        return if (kotlin.math.abs(totalY) >= kotlin.math.abs(totalX)) {
            DragAxis.VERTICAL
        } else {
            DragAxis.HORIZONTAL
        }
    }
}
