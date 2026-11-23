/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Small, consistent taps of vibration for the glass controls. They follow the system haptics setting. */
object SounHaptics {
    /** A finger landing on a control that follows it (progress bar, swipe). */
    fun touch(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** A light tick: scrubbing released, a swipe passing its threshold. */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** A definite action such as liking a song. */
    fun confirm(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }
}
