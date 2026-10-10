package com.morsepad.app

import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.morsepad.app.ui.MorsePadRoot

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as MorsePadApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After process recreation the navigation back stack restores itself;
        // only a fresh launch should jump into the last unfinished note.
        val restoreLastNote = savedInstanceState == null
        setContent { MorsePadRoot(container, restoreLastNote) }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val signals = container.touchSignals
        signals.pointerUpIsCanceled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            event.actionMasked == MotionEvent.ACTION_POINTER_UP &&
            event.flags and MotionEvent.FLAG_CANCELED != 0
        try {
            return super.dispatchTouchEvent(event)
        } finally {
            signals.pointerUpIsCanceled = false
        }
    }
}
