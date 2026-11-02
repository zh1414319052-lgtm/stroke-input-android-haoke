package io.github.yawnoc.strokeinput;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class HaokeUpdateClientTest {
    private JSONObject fixture;
    private HaokeUpdateVerifier.Config config;
    private final HaokeUpdateVerifier.Device device = new HaokeUpdateVerifier.Device(1, 35, "arm64-v8a");
    private long now;
    private MemoryStore store;
    private Source source;
    private HaokeUpdateClient client;
    private static final class MemoryStore implements HaokeUpdateClient.Store {
        String json; int writes, failWrite = -1;
        @Override public String read() { return json; }
        @Override public boolean write(String value) { if (++writes == failWrite) return false; json = value; return true; }
    }
    private static final class Source implements HaokeUpdateClient.Fetcher {
        byte[] bytes; int calls, cancellations; boolean fail;
        CountDownLatch entered, release;
        @Override public byte[] fetch(long generation) throws Exception {
            calls++; if (entered != null) entered.countDown();
            if (release != null && !release.await(5, TimeUnit.SECONDS)) throw new AssertionError("test timed out");
            if (fail) throw new java.io.IOException("synthetic offline"); return bytes;
        }
        @Override public void cancel() { cancellations++; }
    }
    @Before public void setup() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/updates/server-android-v2.json")) {
            assertNotNull(input); ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            fixture = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
        now = fixture.getLong("testNow");
        config = new HaokeUpdateVerifier.Config(fixture.getString("softwareId"), fixture.getString("applicationId"), "stable",
                fixture.getJSONObject("identities").getJSONObject("Android").getString("certificateSha256"),
                Collections.singletonMap(fixture.getString("keyId"), fixture.getString("publicKeyBase64")));
        store = new MemoryStore(); source = new Source(); source.bytes = bytes("published");
        client = newClient();
    }
    private HaokeUpdateClient newClient() {
        HaokeUpdateClient result = new HaokeUpdateClient(config, device, store, source, () -> now);
        result.setAllowed(true); return result;
    }
    private byte[] bytes(String phase) throws Exception {
        JSONArray values = fixture.getJSONArray("fixtures");
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.getJSONObject(i);
            if (phase.equals(value.getString("phase"))) return value.getJSONObject("envelope").toString().getBytes(StandardCharsets.UTF_8);
        }
        throw new AssertionError(phase);
    }
    @Test public void foregroundFailureRecoversAndManualBypassesDebounce() {
        source.fail = true; assertEquals("silent-error", client.check(false).kind);
        source.fail = false; assertEquals("throttled", client.check(false).kind); assertEquals(1, source.calls);
        assertEquals("available", client.check(true).kind); assertEquals(2, source.calls);
        now += 60_000; assertEquals("available", client.check(false).kind);
    }
    @Test public void actualPresentationIsAcknowledgedNotFetchAndManualCanShowSuppressed() throws Exception {
        HaokeUpdateClient.Result result = client.check(false); long version = result.release.versionCode;
        assertEquals(-1, HaokeUpdateState.decode(store.json, config.scope()).promptedVersionCode);
        AtomicInteger shown = new AtomicInteger(); assertTrue(client.present(shown::incrementAndGet, false));
        assertEquals(version, HaokeUpdateState.decode(store.json, config.scope()).promptedVersionCode);
        client.dispose(); client = newClient(); now += 60_000;
        assertEquals("suppressed", client.check(false).kind);
        assertFalse(client.present(shown::incrementAndGet, false));
        assertTrue(client.present(shown::incrementAndGet, true)); assertEquals(2, shown.get());
    }
    @Test public void lifecycleCancellationRejectsLateResultAndDoesNotConsumePrompt() throws Exception {
        source.entered = new CountDownLatch(1); source.release = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<HaokeUpdateClient.Result> result = worker.submit(() -> client.check(true));
            assertTrue(source.entered.await(2, TimeUnit.SECONDS)); client.setAllowed(false); source.release.countDown();
            assertEquals("cancelled", result.get(2, TimeUnit.SECONDS).kind);
            assertTrue(source.cancellations > 0); assertNull(client.pageUrlFromUserGesture());
            assertFalse(client.present(() -> fail("late UI"), true));
            assertEquals(-1, HaokeUpdateState.decode(store.json, config.scope()).promptedVersionCode);
            client.setAllowed(true); assertEquals("available", client.check(true).kind);
        } finally { source.release.countDown(); worker.shutdownNow(); }
    }
    @Test public void concurrentChecksHaveOneFlightAndNoDuplicateFetch() throws Exception {
        source.entered = new CountDownLatch(1); source.release = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<HaokeUpdateClient.Result> result = worker.submit(() -> client.check(false));
            assertTrue(source.entered.await(2, TimeUnit.SECONDS)); assertEquals("deferred", client.check(true).kind);
            assertEquals(1, source.calls); source.release.countDown(); assertEquals("available", result.get(2, TimeUnit.SECONDS).kind);
        } finally { source.release.countDown(); worker.shutdownNow(); }
    }
    @Test public void storageFailureBeforeRequestAfterVerificationOrAtPresentationFailsClosed() {
        store.failWrite = 1; assertEquals("error", client.check(true).kind); assertEquals(0, source.calls);
        store = new MemoryStore(); store.failWrite = 2; client = newClient();
        assertEquals("error", client.check(true).kind); assertNull(client.pageUrlFromUserGesture());
        store = new MemoryStore(); store.failWrite = 3; client = newClient();
        assertEquals("available", client.check(true).kind);
        assertFalse(client.present(() -> fail("must not display before durable ack"), true));
        assertNull(client.pageUrlFromUserGesture()); assertFalse(client.ignore());
    }
    @Test public void thrownDialogRollsBackAcknowledgementAndCancellationClearsAction() throws Exception {
        assertEquals("available", client.check(true).kind);
        assertFalse(client.present(() -> { throw new IllegalStateException("synthetic display failure"); }, true));
        assertEquals(-1, HaokeUpdateState.decode(store.json, config.scope()).promptedVersionCode);
        assertNull(client.pageUrlFromUserGesture());
        assertEquals("available", client.check(true).kind); assertNotNull(client.pageUrlFromUserGesture());
        client.cancel(); assertNull(client.pageUrlFromUserGesture());
    }
    @Test public void withdrawalOrFailedRefreshNeverRevivesPriorOffer() throws Exception {
        assertEquals("available", client.check(true).kind); source.bytes = bytes("withdrawn");
        assertEquals("withdrawn", client.check(true).kind); assertNull(client.pageUrlFromUserGesture());
        source.bytes = bytes("published"); assertEquals("error", client.check(true).kind); // revision rollback
        assertNull(client.pageUrlFromUserGesture());
        source.fail = true; assertEquals("error", client.check(true).kind); assertNull(client.pageUrlFromUserGesture());
    }
    @Test public void laterIgnoreAndManualBehaviorPersistWithoutAffectingVerification() throws Exception {
        assertEquals("available", client.check(true).kind); assertTrue(client.present(() -> {}, true)); assertTrue(client.later());
        now += 60_000; client = newClient(); assertEquals("suppressed", client.check(false).kind);
        assertEquals("available", client.check(true).kind); assertTrue(client.present(() -> {}, true)); assertTrue(client.ignore());
        now += 60_000; client = newClient(); assertEquals("suppressed", client.check(false).kind);
        assertEquals("available", client.check(true).kind);
        byte[] invalid = source.bytes.clone(); invalid[5] ^= 1; source.bytes = invalid;
        assertEquals("error", client.check(true).kind); assertNull(client.pageUrlFromUserGesture());
    }
}
