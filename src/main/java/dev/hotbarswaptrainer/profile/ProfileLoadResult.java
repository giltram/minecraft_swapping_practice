package dev.hotbarswaptrainer.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProfileLoadResult {
    private final List<LoadoutProfile> profiles;
    private final List<String> errors;

    public ProfileLoadResult(List<LoadoutProfile> profiles, List<String> errors) {
        this.profiles = Collections.unmodifiableList(new ArrayList<>(profiles));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public List<LoadoutProfile> getProfiles() { return profiles; }
    public List<String> getErrors() { return errors; }
}
