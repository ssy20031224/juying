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
}
