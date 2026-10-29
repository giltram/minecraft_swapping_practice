package dev.hotbarswaptrainer.input;

/** Observations only. Initialization and restoration never call these methods. */
public final class PlayerActionTracker {
    private int attempts, effective, selections, noOps, forbidden;
    public void swap(boolean changed) { attempts++; if (changed) effective++; else noOps++; }
    public void selection() { selections++; }
    public void forbidden() { forbidden++; }
    public int getAttempts() { return attempts; }
    public int getEffective() { return effective; }
    public int getSelections() { return selections; }
    public int getNoOps() { return noOps; }
    public int getForbidden() { return forbidden; }
}
