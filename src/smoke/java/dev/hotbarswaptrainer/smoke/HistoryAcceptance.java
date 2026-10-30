package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.InventorySnapshot;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.stats.*;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.minecraft.server.network.ServerPlayerEntity;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static dev.hotbarswaptrainer.smoke.AcceptanceSupport.*;

/** Post-restore history and real filesystem failure checks; confined to the owned test mod. */
final class HistoryAcceptance {
    static String run(ServerPlayerEntity player, InventorySnapshot baseline, Path output) throws Exception {
        ResultStore original = (ResultStore) field(TrainerMod.SESSIONS,"results");
        List<Map<String,Object>> checks = new ArrayList<>();
        final int[] saves = {0};
        ResultStore observed = new ResultStore() {
            public void save(UUID id, SessionResult result) throws IOException {
                try { restored(player,baseline); } catch (Exception e) { throw new IOException("History ran before confirmed restoration",e); }
                saves[0]++; original.save(id,result);
            }
            public Optional<SessionResult> findLatest(UUID id) { return original.findLatest(id); }
            public void forget(UUID id) { original.forget(id); }
            public void clear() { original.clear(); }
        };
        set(TrainerMod.SESSIONS,"results",observed);
        try {
            for (TrainingMode mode : TrainingMode.values()) {
                TrainerMod.SESSIONS.handle(player,TrainerProtocol.Request.start(mode,"default",2,true));
                require(phase(player).equals("PREPARING"),"history finite START");
                UUID id = (UUID) field(session(player),"id");
                // A later START cannot change frozen options of the active session.
                TrainerMod.SESSIONS.handle(player,TrainerProtocol.Request.start(mode,"default",999,false));
                for (int i=0;i<2;i++) {
                    runTask(player);
                    if(i==0) {
                        TrainerMod.SESSIONS.guardAction(player,"ignored after frozen success");
                        set(session(player),"deadlineTick",player.getServer().getTicks()-1);
                        TrainerMod.SESSIONS.tick(player.getServer());
                    }
                }
                set(session(player),"deadlineTick",player.getServer().getTicks()-1); TrainerMod.SESSIONS.tick(player.getServer());
                restored(player,baseline);
                SessionResult r=original.findLatest(player.getUuid()).get();
                require(r.getSessionId().equals(id)&&r.getSuccessCount()==2&&r.getRequestedSuccesses()==2,"frozen finite result");
                require(r.getMetadata().targetHints&&r.getMetadata().endState==SessionMetadata.EndState.COMPLETED,"frozen metadata");
                checks.add(check(mode+"-finite","Two real vanilla task solutions; frozen hints/goal; history saved only after both restoration readbacks"));
            }
            startAt(player,"RUNNING"); TrainerMod.SESSIONS.guardAction(player,"history invalid fixture");
            require(phase(player).equals("INVALID"),"invalid attempt");
            set(session(player),"deadlineTick",player.getServer().getTicks()-1); TrainerMod.SESSIONS.tick(player.getServer());
            runTask(player); TrainerMod.SESSIONS.stop(player,"Stopped"); restored(player,baseline);
            SessionResult partial=original.findLatest(player.getUuid()).get();
            require(partial.getRequestedSuccesses()==0&&partial.getSuccessCount()==1&&partial.getTasks().get(0).getOutcome()==TaskResult.Outcome.INVALID,"invalid/unlimited result");
            checks.add(check("unlimited-invalid-stop","Invalid attempt retained, one success, manual stop restores; no finite PB"));
            startAt(player,"RUNNING"); TrainerMod.SESSIONS.stop(player,"Stopped"); restored(player,baseline);
            require(original.findLatest(player.getUuid()).get().getTasks().get(0).getOutcome()==TaskResult.Outcome.STOPPED,"STOPPED task missing");
            checks.add(check("zero-success-stop","Running task stopped with zero successes"));
            Path faultRoot=Files.createTempDirectory(output,"history-denied-");
            Files.write(faultRoot.resolve(player.getUuid().toString()),new byte[]{7});
            set(TrainerMod.SESSIONS,"results",new HistoryStore(faultRoot));
            startAt(player,"RUNNING"); TrainerMod.SESSIONS.stop(player,"Stopped");
            restored(player,baseline); require(session(player)==null,"Statistics failure relocked session");
            require(Arrays.equals(new byte[]{7},Files.readAllBytes(faultRoot.resolve(player.getUuid().toString()))),"fault original changed");
            checks.add(check("history-write-failure","Actual filesystem path write rejection; original byte retained; both saved inventories exact; session/guards released"));
            set(TrainerMod.SESSIONS,"results",new ResultStore() {
                public void save(UUID id,SessionResult r) { throw new IllegalStateException("Injected stats runtime failure"); }
                public Optional<SessionResult> findLatest(UUID id) { return Optional.empty(); }
                public void forget(UUID id) { } public void clear() { }
            });
            startAt(player,"RUNNING"); TrainerMod.SESSIONS.stop(player,"Stopped"); restored(player,baseline);
            require(session(player)==null,"Runtime statistics failure relocked session");
            checks.add(check("statistics-runtime-failure","Unchecked result store failure cannot relock or retain training items"));
            startAt(player,"RUNNING"); HistoryNotificationFault.armed=true; HistoryNotificationFault.attempts=0;
            try {
                TrainerMod.SESSIONS.stop(player,"Stopped"); restored(player,baseline);
                require(session(player)==null&&HistoryNotificationFault.attempts>=2,"Notification failure interrupted restore/release");
            } finally { HistoryNotificationFault.armed=false; }
            checks.add(check("notification-failure","RESTORING and IDLE sends throw while statistics also fails; original inventory saved and session/guards released"));
        } finally { set(TrainerMod.SESSIONS,"results",original); }
        require(saves[0]==4,"Unexpected successful persistence call count");
        Path historyRoot=(Path)field(original,"root"); HistorySnapshot history=new HistoryStore(historyRoot).read(player.getUuid());
        require(history.errors.isEmpty()&&history.records.size()>=4,"Fresh history read failed: "+history.errors);
        original.forget(player.getUuid()); original.clear();
        require(new HistoryStore(historyRoot).read(player.getUuid()).records.size()==history.records.size(),"Memory cleanup deleted disk history");
        checks.add(check("fresh-read-memory-cleanup","New store reads complete valid history; forget/clear leave disk records"));
        json(output.resolve("history-checks.json"),checks);
        return checks.size()+" history lifecycle/failure checks passed";
    }
    private static void runTask(ServerPlayerEntity player) throws Exception {
        signal(player,TrainerProtocol.Action.READY);
        require(phase(player).equals("COUNTDOWN"),"READY countdown");
        set(session(player),"deadlineTick",player.getServer().getTicks()-1); TrainerMod.SESSIONS.tick(player.getServer());
        signal(player,TrainerProtocol.Action.GO_ACK); require(phase(player).equals("RUNNING"),"GO_ACK running");
        solve(player); require(phase(player).equals("SUCCESS"),"real task solution");
    }
}
