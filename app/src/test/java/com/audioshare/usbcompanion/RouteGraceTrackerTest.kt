package com.audioshare.usbcompanion

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteGraceTrackerTest {
    private val tracker = RouteGraceTracker(graceNanos = 2_000_000_000L)

    @Test
    fun transientMissingRouteGetsFreshGraceWindow() {
        assertEquals(
            RouteGraceTracker.Observation.GRACE,
            tracker.observe(10_000L, routed = false),
        )
        assertEquals(
            RouteGraceTracker.Observation.GRACE,
            tracker.observe(500_000_000L, routed = false),
        )
        assertEquals(
            RouteGraceTracker.Observation.VALID,
            tracker.observe(500_000_001L, routed = true),
        )
        assertEquals(
            RouteGraceTracker.Observation.GRACE,
            tracker.observe(3_000_000_000L, routed = false),
        )
    }

    @Test
    fun continuouslyMissingRouteExpires() {
        assertEquals(
            RouteGraceTracker.Observation.GRACE,
            tracker.observe(0L, routed = false),
        )
        assertEquals(
            RouteGraceTracker.Observation.EXPIRED,
            tracker.observe(2_000_000_000L, routed = false),
        )
    }

    @Test
    fun concreteRouteResetsGraceAndStaysValid() {
        assertEquals(
            RouteGraceTracker.Observation.GRACE,
            tracker.observe(1L, routed = false),
        )
        assertEquals(
            RouteGraceTracker.Observation.VALID,
            tracker.observe(1_000_000_001L, routed = true),
        )
        assertEquals(
            RouteGraceTracker.Observation.VALID,
            tracker.observe(1_500_000_001L, routed = true),
        )
        // A fresh missing route after a valid one gets a new grace window,
        // rather than reusing an expired deadline.
        assertEquals(
            RouteGraceTracker.Observation.GRACE,
            tracker.observe(4_000_000_003L, routed = false),
        )
    }

    @Test
    fun routeReturningInsideGraceIsValid() {
        assertEquals(
            RouteGraceTracker.Observation.GRACE,
            tracker.observe(0L, routed = false),
        )
        assertEquals(
            RouteGraceTracker.Observation.VALID,
            tracker.observe(1_999_999_999L, routed = true),
        )
    }
}
