package com.morsepad.app.input

/**
 * Carries information that Compose's pointer API does not expose.
 *
 * On Android 13+ the system can withdraw a single pointer (for example a palm) by sending
 * ACTION_POINTER_UP with FLAG_CANCELED. Compose reports that as an ordinary "up". The
 * activity sets [pointerUpIsCanceled] for the duration of that one event; touch dispatch is
 * synchronous, so the chord pad reads the flag while handling exactly that event.
 * Main thread only.
 */
class TouchSignals {
    var pointerUpIsCanceled: Boolean = false
        internal set
}
