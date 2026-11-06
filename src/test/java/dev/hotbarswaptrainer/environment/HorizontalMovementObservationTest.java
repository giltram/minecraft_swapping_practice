package dev.hotbarswaptrainer.environment;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizontalMovementObservationTest {
    @Test public void acceptedHorizontalSegmentsUseBlocksAndDoNotCancelAtTurns() {
        HorizontalMovementObservation observation = new HorizontalMovementObservation();
        observation.accepted(0, 0, 0.3, 0.4);
        assertEquals(0.5, observation.distance(), 1e-12);
        observation.accepted(0.3, 0.4, 0, 0);
        assertEquals(0.5, observation.distance(), 1e-12);
    }

    @Test public void stationaryRotationAndVerticalOnlyJumpHaveZeroHorizontalDistance() {
        HorizontalMovementObservation observation = new HorizontalMovementObservation();
        observation.accepted(10, -20, 10, -20);
        assertEquals(0, observation.distance(), 0);
    }

    @Test public void correctionResetAndNextPacketDoNotSpanTheTeleport() {
        HorizontalMovementObservation observation = new HorizontalMovementObservation();
        observation.accepted(0, 0, 0.2, 0);
        observation.reset(); assertEquals(0, observation.distance(), 0);
        observation.accepted(100, 100, 100.1, 100);
        assertEquals(0.1, observation.distance(), 1e-12);
    }

    @Test public void diagnosticsHaveNoMinimumSpeedOrTimingThresholdAndRejectNonfiniteSegments() {
        HorizontalMovementObservation observation = new HorizontalMovementObservation();
        observation.accepted(0, 0, 1e-12, 0);
        assertEquals(1e-12, observation.distance(), 0);
        observation.accepted(Double.NaN, 0, 1, 0); assertEquals(0, observation.distance(), 0);
        observation.accepted(0, 0, Double.POSITIVE_INFINITY, 0); assertEquals(0, observation.distance(), 0);
    }
}
