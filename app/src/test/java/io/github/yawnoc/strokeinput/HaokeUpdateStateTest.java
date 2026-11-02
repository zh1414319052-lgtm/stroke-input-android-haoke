package io.github.yawnoc.strokeinput;

import org.junit.Test;
import static org.junit.Assert.*;

public class HaokeUpdateStateTest {
    @Test public void debounceIsOneMinuteAndClockRollbackDoesNotLockOut() {
        HaokeUpdateState state = new HaokeUpdateState("product"); state.lastCheckAt = 100_000;
        assertEquals(60_000, state.retryAfter(100_000)); assertEquals(1, state.retryAfter(159_999));
        assertEquals(0, state.retryAfter(160_000)); assertEquals(0, state.retryAfter(50_000));
    }
    @Test public void actualPresentationAcknowledgementSurvivesRestartAndNewerVersionAppears() throws Exception {
        HaokeUpdateState state = new HaokeUpdateState("product");
        assertFalse(state.suppressed(12, 100)); state.acknowledge(12);
        state = HaokeUpdateState.decode(state.encode(), "product");
        assertTrue(state.suppressed(12, 100)); assertFalse(state.suppressed(13, 100));
    }
    @Test public void laterRearmsOnlySameVersionAfter24HoursAndIgnorePersists() throws Exception {
        HaokeUpdateState state = new HaokeUpdateState("product"); state.acknowledge(12); state.later(12, 100);
        state = HaokeUpdateState.decode(state.encode(), "product");
        assertTrue(state.suppressed(12, 200)); assertFalse(state.suppressed(13, 200));
        assertFalse(state.suppressed(12, 100 + HaokeUpdateVerifier.DAY_MS));
        state.ignoredVersionCode = 12;
        assertTrue(HaokeUpdateState.decode(state.encode(), "product").suppressed(12, 100 + HaokeUpdateVerifier.DAY_MS));
        assertFalse(state.suppressed(13, 100 + HaokeUpdateVerifier.DAY_MS));
    }
    @Test public void oldSchedulingFieldsMigrateWithoutLosingCheckpointOrExplicitChoice() throws Exception {
        String json = "{\"scope\":\"product\",\"lastAutomaticAt\":500,\"ignoredVersionCode\":12,\"snoozedUntil\":0,\"checkpoint\":{\"revision\":4,\"digest\":\"" + "a".repeat(64) + "\",\"maxVersionCode\":12}}";
        HaokeUpdateState state = HaokeUpdateState.decode(json, "product");
        assertEquals(0, state.retryAfter(600)); assertEquals(4, state.checkpoint.revision);
        assertEquals(12, state.checkpoint.maxVersionCode); assertTrue(state.suppressed(12, 600));
    }
    @Test public void wrongScopeOrCorruptStateFailsClosed() throws Exception {
        try { HaokeUpdateState.decode("{\"scope\":\"other\"}", "product"); fail(); }
        catch (HaokeUpdateVerifier.Failure ex) { assertEquals("INVALID_STORED_STATE", ex.code); }
        try { HaokeUpdateState.decode("{\"scope\":\"product\",\"checkpoint\":{\"revision\":1,\"digest\":\"bad\",\"maxVersionCode\":2}}", "product"); fail(); }
        catch (HaokeUpdateVerifier.Failure ex) { assertEquals("INVALID_STORED_STATE", ex.code); }
    }
}
