package com.androsaver

import android.view.KeyEvent

internal object DiagnosticControls {
    enum class Action { UP, DOWN, PAGE_UP, PAGE_DOWN, DISMISS, CONSUME, PASS }

    fun action(key: Int): Action = when (key) {
        KeyEvent.KEYCODE_DPAD_UP -> Action.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Action.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Action.PAGE_UP
        KeyEvent.KEYCODE_DPAD_RIGHT -> Action.PAGE_DOWN
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> Action.DISMISS
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> Action.PASS
        else -> Action.CONSUME
    }

    fun scrollPosition(current: Int, viewport: Int, content: Int, action: Action): Int {
        val step = when (action) {
            Action.UP -> -maxOf(1, viewport / 3)
            Action.DOWN -> maxOf(1, viewport / 3)
            Action.PAGE_UP -> -viewport
            Action.PAGE_DOWN -> viewport
            else -> 0
        }
        return (current + step).coerceIn(0, maxOf(0, content - viewport))
    }
}
