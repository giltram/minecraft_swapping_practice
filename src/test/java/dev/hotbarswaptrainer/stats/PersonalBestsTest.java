package dev.hotbarswaptrainer.stats;

import dev.hotbarswaptrainer.task.TrainingMode;
import org.junit.Test;
import java.util.*;
import static dev.hotbarswaptrainer.stats.HistoryStoreTest.*;
import static org.junit.Assert.*;

public class PersonalBestsTest {
    private final UUID player = UUID.randomUUID();
    private SessionResult r(int goal, boolean hints, String hash, SessionMetadata.EndState state, long... times) {
        TaskResult[] tasks = new TaskResult[times.length];
        for (int i=0;i<times.length;i++) tasks[i] = task(TaskResult.Outcome.SUCCESS,times[i]);
        return record(player,UUID.randomUUID(),200,goal,hints,hash,state,tasks);
    }
    private PersonalBests pb(SessionResult conditions, SessionResult... records) {
        return PersonalBests.forConditions(new HistorySnapshot(player,Arrays.asList(records),Collections.emptyList()),conditions);
    }
    @Test public void onlyCompletedExactFiniteGoalsQualifyButPartialAndUnlimitedKeepSingleTaskPb() {
        SessionResult complete = r(2,false,HASH,SessionMetadata.EndState.COMPLETED,20,40);
        SessionResult partial = r(2,false,HASH,SessionMetadata.EndState.STOPPED,5);
        SessionResult unlimited = r(0,false,HASH,SessionMetadata.EndState.STOPPED,3);
        SessionResult disconnected = r(2,false,HASH,SessionMetadata.EndState.DISCONNECTED,2,3);
        SessionResult aborted = r(2,false,HASH,SessionMetadata.EndState.ABORTED,1,2);
        PersonalBests pb = pb(complete,complete,partial,unlimited,disconnected,aborted);
        assertEquals(1,pb.task.get().nanos); assertEquals(60,pb.total.get().nanos); assertEquals(30,pb.average.get().nanos);
        assertFalse(pb(unlimited,complete,unlimited).total.isPresent());
        assertFalse(pb(partial,partial).total.isPresent());
    }
    @Test public void groupsSeparateHashHintsModePlayerVersionsEnvironmentAndSessionLength() {
        SessionResult baseline = r(2,false,HASH,SessionMetadata.EndState.COMPLETED,50,50);
        SessionResult otherHints = r(2,true,HASH,SessionMetadata.EndState.COMPLETED,1,1);
        SessionResult otherHash = r(2,false,HASH.replace('a','b'),SessionMetadata.EndState.COMPLETED,1,1);
        SessionResult otherLength = r(1,false,HASH,SessionMetadata.EndState.COMPLETED,10);
        List<SessionResult> list = new ArrayList<>(Arrays.asList(baseline,otherHints,otherHash,otherLength));
        SessionMetadata m = baseline.getMetadata();
        for (int i=0;i<5;i++) {
            SessionMetadata changed = new SessionMetadata(i==0 ? UUID.randomUUID() : player,100,200,"old-id","renamed",false,
                    i==1 ? "unknown" : SessionMetadata.TIMING,i==2 ? "unknown" : SessionMetadata.SCORING,
                    i==3 ? "unknown" : SessionMetadata.ENVIRONMENT,SessionMetadata.EndState.COMPLETED);
            list.add(new SessionResult(UUID.randomUUID(),i==4 ? TrainingMode.FULL_INVENTORY : TrainingMode.SINGLE_ITEM,HASH,2,
                    Arrays.asList(task(TaskResult.Outcome.SUCCESS,1),task(TaskResult.Outcome.SUCCESS,1)),changed));
        }
        PersonalBests pb = pb(baseline,list.toArray(new SessionResult[0]));
        assertEquals(10,pb.task.get().nanos); assertEquals(100,pb.total.get().nanos);
        assertEquals(50,pb.average.get().nanos);
    }
    @Test public void invalidStoppedZeroAndUnknownMetadataCannotBecomeFastest() {
        SessionResult reference = r(1,false,HASH,SessionMetadata.EndState.COMPLETED,30);
        SessionResult bad = record(player,UUID.randomUUID(),200,0,false,HASH,SessionMetadata.EndState.STOPPED,
                task(TaskResult.Outcome.INVALID,1),task(TaskResult.Outcome.STOPPED,1));
        SessionResult legacy = new SessionResult(UUID.randomUUID(),TrainingMode.SINGLE_ITEM,HASH,1,Arrays.asList(task(TaskResult.Outcome.SUCCESS,1)));
        assertFalse(pb(bad,bad).task.isPresent()); assertFalse(pb(legacy,reference,legacy).comparable);
        assertEquals(30,pb(reference,reference,bad,legacy).task.get().nanos);
        assertFalse(pb(reference,r(0,false,HASH,SessionMetadata.EndState.STOPPED)).task.isPresent());
    }
    @Test public void tiesUseEarliestEndThenUuidThenAttemptOrderAndCorruptionWarns() {
        SessionResult later = record(player,new UUID(0,1),300,1,false,HASH,SessionMetadata.EndState.COMPLETED,task(TaskResult.Outcome.SUCCESS,10));
        TaskResult first = task(TaskResult.Outcome.SUCCESS,10), second = task(TaskResult.Outcome.SUCCESS,10);
        SessionResult early = record(player,new UUID(0,2),200,2,false,HASH,SessionMetadata.EndState.COMPLETED,first,second);
        SessionResult tied = record(player,new UUID(0,3),200,2,false,HASH,SessionMetadata.EndState.COMPLETED,task(TaskResult.Outcome.SUCCESS,10),task(TaskResult.Outcome.SUCCESS,10));
        HistorySnapshot history = new HistorySnapshot(player,Arrays.asList(tied,later,early),Arrays.asList("corrupt file"));
        PersonalBests pb = PersonalBests.forConditions(history,early);
        assertTrue(pb.incomplete); assertEquals(early.getSessionId(),pb.task.get().sessionId);
        assertEquals(first.getTaskId(),pb.task.get().taskId); assertEquals(early.getSessionId(),pb.total.get().sessionId);
    }
    @Test public void successfulSumExcludesInvalidAttemptsAndNameSnapshotDoesNotAffectGrouping() {
        SessionResult result = record(player,UUID.randomUUID(),200,1,false,HASH,SessionMetadata.EndState.COMPLETED,
                task(TaskResult.Outcome.INVALID,999),task(TaskResult.Outcome.SUCCESS,10));
        SessionMetadata m = result.getMetadata();
        SessionResult renamed = new SessionResult(UUID.randomUUID(),result.getMode(),HASH,1,result.getTasks(),
                new SessionMetadata(player,100,201,"new-id","new name",false,m.timingVersion,m.scoringVersion,m.environment,m.endState));
        assertEquals(10,pb(renamed,result).total.get().nanos);
        assertEquals("Old profile name",result.getMetadata().profileName);
    }
    @Test public void modePbMergesProfilesHintsGoalsAndPartialSessionsButKeepsModesSeparate() {
        SessionResult baseline = r(2, false, HASH, SessionMetadata.EndState.COMPLETED, 50, 60);
        SessionResult hints = r(0, true, HASH, SessionMetadata.EndState.STOPPED, 30);
        SessionResult profile = r(10, false, HASH.replace('a', 'b'), SessionMetadata.EndState.DISCONNECTED, 20);
        SessionResult full = new SessionResult(UUID.randomUUID(), TrainingMode.FULL_INVENTORY, HASH, 1,
                Arrays.asList(task(TaskResult.Outcome.SUCCESS, 10)), baseline.getMetadata());
        HistorySnapshot history = new HistorySnapshot(player, Arrays.asList(baseline, hints, profile, full), Collections.emptyList());
        assertEquals(20, PersonalBests.fastestTaskForMode(history, TrainingMode.SINGLE_ITEM).get().nanos);
        assertEquals(10, PersonalBests.fastestTaskForMode(history, TrainingMode.FULL_INVENTORY).get().nanos);
    }
    @Test public void modePbExcludesInvalidZeroLegacyUnknownVersionsAndOtherPlayers() {
        SessionResult eligible = r(0, false, HASH, SessionMetadata.EndState.STOPPED, 30);
        SessionResult failed = record(player, UUID.randomUUID(), 200, 0, false, HASH, SessionMetadata.EndState.STOPPED,
                task(TaskResult.Outcome.INVALID, 1), task(TaskResult.Outcome.STOPPED, 1), task(TaskResult.Outcome.SUCCESS, 0));
        List<SessionResult> records = new ArrayList<>(Arrays.asList(eligible, failed));
        records.add(new SessionResult(UUID.randomUUID(), TrainingMode.SINGLE_ITEM, HASH, 0,
                Arrays.asList(task(TaskResult.Outcome.SUCCESS, 1))));
        for (int i = 0; i < 4; i++) {
            SessionMetadata m = new SessionMetadata(i == 0 ? UUID.randomUUID() : player, 100, 200, "id", "name", true,
                    i == 1 ? "unknown" : SessionMetadata.TIMING, i == 2 ? "unknown" : SessionMetadata.SCORING,
                    i == 3 ? "unknown" : SessionMetadata.ENVIRONMENT, SessionMetadata.EndState.STOPPED);
            records.add(new SessionResult(UUID.randomUUID(), TrainingMode.SINGLE_ITEM, HASH, 0,
                    Arrays.asList(task(TaskResult.Outcome.SUCCESS, 1)), m));
        }
        HistorySnapshot history = new HistorySnapshot(player, records, Arrays.asList("corrupt"));
        assertEquals(30, PersonalBests.fastestTaskForMode(history, TrainingMode.SINGLE_ITEM).get().nanos);
        assertFalse(PersonalBests.fastestTaskForMode(history, TrainingMode.FULL_INVENTORY).isPresent());
        assertFalse(PersonalBests.fastestTaskForMode(new HistorySnapshot(player, Collections.emptyList(),
                Collections.emptyList()), TrainingMode.SINGLE_ITEM).isPresent());
    }
    @Test public void modePbTiesRetainDeterministicSessionAndAttemptOrder() {
        TaskResult first = task(TaskResult.Outcome.SUCCESS, 10), second = task(TaskResult.Outcome.SUCCESS, 10);
        SessionResult early = record(player, new UUID(0, 1), 200, 0, true, HASH, SessionMetadata.EndState.STOPPED, first, second);
        SessionResult later = record(player, new UUID(0, 2), 300, 1, false, HASH, SessionMetadata.EndState.COMPLETED,
                task(TaskResult.Outcome.SUCCESS, 10));
        PersonalBests.Best best = PersonalBests.fastestTaskForMode(new HistorySnapshot(player, Arrays.asList(later, early),
                Collections.emptyList()), TrainingMode.SINGLE_ITEM).get();
        assertEquals(early.getSessionId(), best.sessionId); assertEquals(first.getTaskId(), best.taskId);
    }

}
