package dev.hotbarswaptrainer.stats;

import dev.hotbarswaptrainer.task.TrainingMode;
import java.util.*;
import java.util.stream.Collectors;

/** One bounded explicit disk read. Queries and PBs operate on this immutable snapshot without I/O. */
public final class HistorySnapshot {
    public static final int PAGE_SIZE = 10;
    public static final Comparator<SessionResult> RECENT_FIRST = Comparator
            .comparingLong((SessionResult r) -> r.getMetadata() == null ? 0 : r.getMetadata().endedAtMillis).reversed()
            .thenComparing(r -> r.getSessionId().toString());
    public final UUID playerId;
    public final List<SessionResult> records;
    public final List<String> errors;

    HistorySnapshot(UUID player, List<SessionResult> records, List<String> errors) {
        this.playerId = player;
        List<SessionResult> sorted = new ArrayList<>(records); sorted.sort(RECENT_FIRST);
        this.records = Collections.unmodifiableList(sorted);
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }
    public Page query(Filter filter, int page) {
        if (page < 0) throw new IllegalArgumentException("Negative page");
        List<SessionResult> matches = records.stream().filter(filter::matches).collect(Collectors.toList());
        int pages = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int actual = Math.min(page, pages - 1), from = actual * PAGE_SIZE;
        return new Page(matches.subList(from, Math.min(from + PAGE_SIZE, matches.size())), actual, pages, matches.size());
    }
    public static final class Filter {
        public final TrainingMode mode;
        public final Boolean hints;
        public final Integer goal;
        public final String profileHash;
        public final SessionMetadata.EndState endState;
        public Filter(TrainingMode mode, Boolean hints, Integer goal, String hash, SessionMetadata.EndState state) {
            if (goal != null && goal < 0) throw new IllegalArgumentException("Invalid goal");
            this.mode = mode; this.hints = hints; this.goal = goal; this.profileHash = hash; this.endState = state;
        }
        public static Filter all() { return new Filter(null, null, null, null, null); }
        boolean matches(SessionResult r) {
            SessionMetadata m = r.getMetadata();
            return (mode == null || r.getMode() == mode) && (goal == null || r.getRequestedSuccesses() == goal)
                    && (profileHash == null || profileHash.equals(r.getProfileHash()))
                    && (hints == null || m != null && m.targetHints == hints)
                    && (endState == null || m != null && m.endState == endState);
        }
    }
    public static final class Page {
        public final List<SessionResult> records;
        public final int index, pages, total;
        private Page(List<SessionResult> rows, int index, int pages, int total) {
            this.records = Collections.unmodifiableList(new ArrayList<>(rows)); this.index = index; this.pages = pages; this.total = total;
        }
    }
}
