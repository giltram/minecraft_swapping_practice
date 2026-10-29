package dev.hotbarswaptrainer.profile;

/** Structural validation only. Mode applicability is assessed in task, after this succeeds. */
public final class ProfileDraftValidation {
    private final LoadoutProfile profile;
    private final String error;

    private ProfileDraftValidation(LoadoutProfile profile, String error) {
        this.profile = profile;
        this.error = error;
    }

    static ProfileDraftValidation valid(LoadoutProfile profile) { return new ProfileDraftValidation(profile, ""); }
    static ProfileDraftValidation invalid(String error) { return new ProfileDraftValidation(null, error); }
    public boolean isValid() { return profile != null; }
    public String getError() { return error; }
    public LoadoutProfile getProfile() {
        if (!isValid()) throw new IllegalStateException("Invalid draft: " + error);
        return profile;
    }
}
