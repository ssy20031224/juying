package com.juying.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerInteractionPolicyTest {
    @Test
    fun naturalLandscapeUsesSidePanelButExplicitFullscreenDoesNot() {
        assertTrue(PlayerInteractionPolicy.showLandscapeSidePanel(true, false))
        assertFalse(PlayerInteractionPolicy.showLandscapeSidePanel(true, true))
        assertFalse(PlayerInteractionPolicy.showLandscapeSidePanel(false, false))
    }

    @Test
    fun brightnessOrVolumeDragCannotStartHoldSpeedGesture() {
        assertFalse(
            PlayerInteractionPolicy.canStartHoldGesture(
                longPressDetected = true,
                controlsLocked = false,
                dragGestureActive = true
            )
        )
        assertTrue(
            PlayerInteractionPolicy.canStartHoldGesture(
                longPressDetected = true,
                controlsLocked = false,
                dragGestureActive = false
            )
        )
    }

    @Test
    fun localModeAllowsHistoryAndFavoritesWithoutAccount() {
        assertTrue(
            PlayerInteractionPolicy.canUseLocalLibrary(
                accountFeaturesDisabled = true,
                accountAvailable = false
            )
        )
        assertFalse(
            PlayerInteractionPolicy.canUseLocalLibrary(
                accountFeaturesDisabled = false,
                accountAvailable = false
            )
        )
    }

    @Test
    fun verticalBrightnessOrVolumeDragCannotTurnIntoSeek() {
        val vertical = PlayerInteractionPolicy.lockDragAxis(
            current = PlayerInteractionPolicy.DragAxis.UNDECIDED,
            totalX = 4f,
            totalY = 18f
        )
        assertTrue(vertical == PlayerInteractionPolicy.DragAxis.VERTICAL)
        assertTrue(
            PlayerInteractionPolicy.lockDragAxis(
                current = vertical,
                totalX = 80f,
                totalY = 20f
            ) == PlayerInteractionPolicy.DragAxis.VERTICAL
        )
    }
}
