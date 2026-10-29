package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.network.TrainerProtocol;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class ClientSessionViewTest {
    @Test
    public void preparingCanResumeAfterScreenCloses() {
        ClientSessionView state = new ClientSessionView();
        state.accept(view(UUID.randomUUID(), UUID.randomUUID(), 1, "PREPARING"));
        state.markInterrupted();
        state.markReady();
        assertTrue(state.readySent());
        assertFalse(state.interruptionSent());
    }
    @Test
    public void oldRevisionsCannotReopenACompletedTask() {
        UUID session = UUID.randomUUID(), task = UUID.randomUUID();
        ClientSessionView state = new ClientSessionView();
        state.accept(view(session, task, 2, "GO"));
        state.markGo();
        state.accept(view(session, task, 4, "SUCCESS"));
        state.accept(view(session, task, 3, "RUNNING"));
        assertEquals("SUCCESS", state.get().phase);
        assertTrue(state.goSent());
    }

    @Test
    public void newTaskResetsAcknowledgementsAndClosedSessionsStayClosed() {
        UUID firstSession = UUID.randomUUID(), secondSession = UUID.randomUUID();
        UUID task = UUID.randomUUID();
        ClientSessionView state = new ClientSessionView();
        state.accept(view(firstSession, task, 2, "PREPARING"));
        state.markReady(); state.markGo(); state.markInterrupted();
        state.accept(view(firstSession, UUID.randomUUID(), 4, "PREPARING"));
        assertFalse(state.readySent()); assertFalse(state.goSent()); assertFalse(state.interruptionSent());
        state.accept(view(secondSession, UUID.randomUUID(), 1, "PREPARING"));
        state.accept(view(firstSession, task, 99, "RUNNING"));
        assertEquals(secondSession, state.get().sessionId);
    }

    private static TrainerProtocol.View view(UUID session, UUID task, long revision, String phase) {
        return new TrainerProtocol.View.Builder().session(session).task(task).revision(revision).phase(phase).build();
    }
}
