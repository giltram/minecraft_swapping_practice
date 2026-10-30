package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.stats.*;
import dev.hotbarswaptrainer.task.TrainingMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class HistoryBrowserTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void refreshGatePagingFilterRetentionAndShrinkingCatalogue() throws Exception {
        Path root=temporary.newFolder().toPath(); UUID player=UUID.randomUUID(); HistoryStore store=new HistoryStore(root);
        boolean[] allowed={false}; HistoryBrowser browser=new HistoryBrowser(store,player,()->allowed[0]);
        assertFalse(browser.refresh()); assertNull(browser.snapshot); allowed[0]=true; assertTrue(browser.refresh());
        assertEquals(0,browser.current().total);
        List<Path> files=new ArrayList<>();
        for(int i=0;i<23;i++) {
            SessionResult r=new SessionResult(UUID.randomUUID(),TrainingMode.SINGLE_ITEM,"legacy",0,Collections.emptyList());
            store.save(player,r); files.add(root.resolve(player.toString()).resolve(r.getSessionId()+".json"));
        }
        assertEquals(0,browser.current().total); // No implicit/per-frame read.
        browser.refresh(); browser.turn(2); assertEquals(2,browser.current().index); assertEquals(3,browser.current().records.size());
        browser.filter(new HistorySnapshot.Filter(TrainingMode.SINGLE_ITEM,null,null,null,null)); browser.turn(2);
        for(Path file:files.subList(1,files.size())) Files.delete(file); // Only fixtures created by this test.
        browser.refresh(); assertEquals(0,browser.page); assertEquals(1,browser.current().total);
        assertEquals(TrainingMode.SINGLE_ITEM,browser.filter.mode);
        allowed[0]=false; assertFalse(browser.refresh()); assertFalse(browser.allowed());
    }
}
