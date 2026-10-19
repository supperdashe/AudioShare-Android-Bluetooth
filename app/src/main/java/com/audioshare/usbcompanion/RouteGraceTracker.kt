package com.audioshare.usbcompanion

/**
 * Keeps a short, renewable grace period for a transiently missing audio route.
 * Android can briefly report no route while rebuilding its audio policy after
 * a Bluetooth or wired-headset transition. A missing route is fatal only when
 * it remains continuous for the full grace window. Any concrete output device
 * (built-in speaker, Bluetooth A2DP, wired headset, USB, ...) is valid.
 */
internal class RouteGraceTracker(
    private val graceNanos: Long,
) {
    private var invalidSinceNanos: Long? = null

    fun observe(nowNanos: Long, routed: Boolean): Observation {
        if (routed) {
            invalidSinceNanos = null
            return Observation.VALID
        }
        val since = invalidSinceNanos ?: nowNanos.also { invalidSinceNanos = it }
        return if (nowNanos - since >= graceNanos) {
            Observation.EXPIRED
        } else {
            Observation.GRACE
        }
    }

    enum class Observation {
        VALID,
        GRACE,
        EXPIRED,
    }
}
