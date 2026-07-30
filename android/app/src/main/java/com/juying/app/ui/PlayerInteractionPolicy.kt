package com.juying.app.ui

/**
 * Pure interaction decisions shared by the player screen and gesture layer.
 * Keeping these free of Compose/Android state makes the edge cases testable.
 */
internal object PlayerInteractionPolicy {
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
}
