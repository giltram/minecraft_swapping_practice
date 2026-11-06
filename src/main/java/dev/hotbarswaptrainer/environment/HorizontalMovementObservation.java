package dev.hotbarswaptrainer.environment;

/** Last accepted packet's horizontal path segment, in blocks. Diagnostic only; never gates results. */
public final class HorizontalMovementObservation {
    private double distance;

    public void accepted(double beforeX, double beforeZ, double afterX, double afterZ) {
        double segment = Math.hypot(afterX - beforeX, afterZ - beforeZ);
        distance = Double.isFinite(segment) ? segment : 0;
    }

    public double distance() { return distance; }
    public void reset() { distance = 0; }
}
