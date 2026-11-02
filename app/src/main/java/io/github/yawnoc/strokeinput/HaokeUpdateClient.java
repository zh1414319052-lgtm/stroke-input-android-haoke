package io.github.yawnoc.strokeinput;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

/** One settings-screen owner. Never instantiated by the IME, recorder or recognition worker. */
final class HaokeUpdateClient {
    interface Store {
        String read() throws Exception;
        boolean write(String json) throws Exception;
    }
    interface Fetcher {
        byte[] fetch(long generation) throws Exception;
        void cancel();
    }
    interface TimeSource { long now(); }
    static final class Release {
        public final long versionCode;
        public final String versionName, notesZh, notesEn, pageUrl;
        Release(HaokeUpdateVerifier.Release value) {
            versionCode = value.versionCode; versionName = value.versionName;
            notesZh = value.notesZh; notesEn = value.notesEn; pageUrl = value.pageUrl;
        }
    }
    static final class Result {
        public final String kind, errorCode;
        public final long retryAfterMs;
        public final Release release;
        Result(String kind, String errorCode, long retryAfterMs, HaokeUpdateVerifier.Release release) {
            this.kind = kind; this.errorCode = errorCode; this.retryAfterMs = retryAfterMs;
            this.release = release == null ? null : new Release(release);
        }
        static Result kind(String kind) { return new Result(kind, null, 0, null); }
    }
    private final HaokeUpdateVerifier.Config config;
    private final HaokeUpdateVerifier.Device device;
    private final Store store;
    private final Fetcher fetcher;
    private final TimeSource clock;
    private final String startupError;
    private boolean allowed, disposed, checking, storageFailed;
    private long generation;
    private HttpsURLConnection connection;
    private HaokeUpdateVerifier.Receipt offer;

    @SuppressWarnings("deprecation")
    HaokeUpdateClient(Context context) {
        config = HaokeUpdateConfig.create();
        HaokeUpdateVerifier.Device actual = null;
        SharedPreferences saved = null;
        String failure = null;
        try {
            Context app = context.getApplicationContext();
            PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), PackageManager.GET_SIGNATURES);
            HaokeUpdateVerifier.require(app.getPackageName().equals(config.applicationId)
                    && info.signatures != null && info.signatures.length == 1
                    && HaokeUpdateVerifier.sha256(info.signatures[0].toByteArray()).equals(config.certificateSha256), "INSTALLED_IDENTITY_MISMATCH");
            String[] abis = Process.is64Bit() ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
            actual = new HaokeUpdateVerifier.Device(Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode,
                    Build.VERSION.SDK_INT, abis.length == 0 ? "" : abis[0]);
            HaokeUpdateVerifier.validateConfig(config, actual);
            saved = app.getSharedPreferences("haoke_updates_v2_" + HaokeUpdateVerifier.sha256(
                    config.scope().getBytes(java.nio.charset.StandardCharsets.UTF_8)), Context.MODE_PRIVATE);
        } catch (Exception ex) {
            failure = ex instanceof HaokeUpdateVerifier.Failure ? ((HaokeUpdateVerifier.Failure) ex).code : "UNAVAILABLE";
        }
        device = actual; startupError = failure;
        SharedPreferences preferences = saved;
        store = preferences == null ? null : new Store() {
            @Override public String read() { return preferences.getString("state", null); }
            @Override public boolean write(String json) { return preferences.edit().putString("state", json).commit(); }
        };
        fetcher = null; clock = System::currentTimeMillis;
    }

    /** Dependency boundary for deterministic failure/lifecycle tests, never a production trust override. */
    HaokeUpdateClient(HaokeUpdateVerifier.Config config, HaokeUpdateVerifier.Device device,
                      Store store, Fetcher fetcher, TimeSource clock) {
        this.config = config; this.device = device; this.store = store; this.fetcher = fetcher; this.clock = clock;
        String failure = null;
        try { HaokeUpdateVerifier.validateConfig(config, device); }
        catch (HaokeUpdateVerifier.Failure ex) { failure = ex.code; }
        startupError = failure;
    }

    synchronized void setAllowed(boolean value) {
        if (allowed != value) { allowed = value; if (!value) cancel(); }
    }
    synchronized void cancel() {
        generation++; offer = null;
        if (connection != null) connection.disconnect();
        if (fetcher != null) fetcher.cancel();
    }
    synchronized void dispose() { disposed = true; allowed = false; cancel(); }
    private synchronized boolean current(long expected) { return !disposed && allowed && generation == expected; }
    private synchronized HaokeUpdateState load() throws HaokeUpdateVerifier.Failure {
        HaokeUpdateVerifier.require(!storageFailed && store != null, "STORAGE_UNAVAILABLE");
        try { return HaokeUpdateState.decode(store.read(), config.scope()); }
        catch (HaokeUpdateVerifier.Failure ex) { throw ex; }
        catch (Exception ex) { throw new HaokeUpdateVerifier.Failure("INVALID_STORED_STATE"); }
    }
    private synchronized void save(HaokeUpdateState state) throws HaokeUpdateVerifier.Failure {
        HaokeUpdateVerifier.require(!storageFailed, "STORAGE_UNAVAILABLE");
        try {
            // commit is intentional: a visible offer must never precede durable rollback/ack state.
            if (!store.write(state.encode())) throw new IllegalStateException();
        } catch (Exception ex) {
            storageFailed = true; offer = null; throw new HaokeUpdateVerifier.Failure("STORAGE_UNAVAILABLE");
        }
    }

    /** Blocking bounded operation; the UI owner supplies one executor and delivers one result. */
    Result check(boolean manual) {
        final long expected;
        synchronized (this) {
            if (disposed || !allowed) return Result.kind("cancelled");
            if (checking) return Result.kind("deferred");
            checking = true; expected = generation; offer = null;
        }
        try {
            if (startupError != null) throw new HaokeUpdateVerifier.Failure(startupError);
            synchronized (this) {
                if (!current(expected)) return Result.kind("cancelled");
                HaokeUpdateState state = load(); long now = clock.now();
                long retryAfter = state.retryAfter(now);
                if (!manual && retryAfter > 0) return new Result("throttled", null, retryAfter, null);
                state.lastCheckAt = now; save(state);
            }
            byte[] bytes = fetcher == null ? fetch(expected) : fetcher.fetch(expected);
            if (!current(expected)) return Result.kind("cancelled");
            HaokeUpdateVerifier.Receipt receipt = HaokeUpdateVerifier.verify(bytes, config, device, null, clock.now());
            synchronized (this) {
                if (!current(expected)) return Result.kind("cancelled");
                long now = clock.now();
                HaokeUpdateVerifier.require(receipt.expiresAt > now, "EXPIRED_OR_FUTURE");
                HaokeUpdateState state = load();
                state.checkpoint = HaokeUpdateVerifier.acceptCheckpoint(receipt.checkpoint, state.checkpoint); save(state);
                if (!"available".equals(receipt.availability)) return Result.kind(receipt.availability);
                offer = receipt;
                return new Result(!manual && state.suppressed(receipt.release.versionCode, now) ? "suppressed" : "available",
                        null, 0, receipt.release);
            }
        } catch (Exception ex) {
            synchronized (this) { offer = null; }
            if (!current(expected)) return Result.kind("cancelled");
            return new Result(manual ? "error" : "silent-error",
                    ex instanceof HaokeUpdateVerifier.Failure ? ((HaokeUpdateVerifier.Failure) ex).code : "UNAVAILABLE", 0, null);
        } finally {
            synchronized (this) { checking = false; }
        }
    }

    private byte[] fetch(long expected) throws IOException, HaokeUpdateVerifier.Failure {
        HaokeUpdateVerifier.require(current(expected), "CANCELLED");
        URL url = new URL(config.feedUrl());
        HaokeUpdateVerifier.require("https".equals(url.getProtocol()) && "haoke.org".equals(url.getHost())
                && url.getPort() == -1 && url.getQuery() == null && url.getRef() == null
                && url.getUserInfo() == null, "UNTRUSTED_URL");
        HttpsURLConnection request = (HttpsURLConnection) url.openConnection();
        request.setInstanceFollowRedirects(false); request.setConnectTimeout(12_000); request.setReadTimeout(12_000);
        request.setUseCaches(false); request.setRequestMethod("GET");
        request.setRequestProperty("Accept", "application/json");
        request.setRequestProperty("Accept-Encoding", "identity");
        request.setRequestProperty("Cache-Control", "no-store");
        request.setRequestProperty("User-Agent", "Haoke-Update/2");
        request.setRequestProperty("Cookie", "");
        synchronized (this) { HaokeUpdateVerifier.require(current(expected), "CANCELLED"); connection = request; }
        ScheduledExecutorService deadline = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "haoke-update-timeout"); thread.setDaemon(true); return thread;
        });
        ScheduledFuture<?> timeout = deadline.schedule(request::disconnect, 12, TimeUnit.SECONDS);
        long start = android.os.SystemClock.elapsedRealtime();
        try {
            HaokeUpdateVerifier.require(request.getResponseCode() == 200, "HTTP_REJECTED");
            String length = request.getHeaderField("Content-Length");
            if (length != null) HaokeUpdateVerifier.require(length.matches("[0-9]{1,9}")
                    && Long.parseLong(length) <= HaokeUpdateVerifier.MAX_ENVELOPE, "ENVELOPE_TOO_LARGE");
            try (InputStream input = request.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    HaokeUpdateVerifier.require(current(expected), "CANCELLED");
                    HaokeUpdateVerifier.require(android.os.SystemClock.elapsedRealtime() - start < 12_000, "TIMEOUT");
                    HaokeUpdateVerifier.require(output.size() + count <= HaokeUpdateVerifier.MAX_ENVELOPE, "ENVELOPE_TOO_LARGE");
                    output.write(buffer, 0, count);
                }
                HaokeUpdateVerifier.require(android.os.SystemClock.elapsedRealtime() - start < 12_000, "TIMEOUT");
                return output.toByteArray();
            }
        } finally {
            timeout.cancel(false); deadline.shutdownNow(); request.disconnect();
            synchronized (this) { if (connection == request) connection = null; }
        }
    }

    private boolean validOffer() {
        return allowed && !disposed && !storageFailed && offer != null && offer.expiresAt > clock.now();
    }
    /** Call synchronously at the actual modal-open boundary; not merely after a fetch. */
    synchronized boolean present(Runnable show, boolean manual) {
        if (!validOffer()) return false;
        HaokeUpdateState previous = null;
        try {
            previous = load();
            if (!manual && previous.suppressed(offer.release.versionCode, clock.now())) return false;
            HaokeUpdateState next = HaokeUpdateState.decode(previous.encode(), config.scope());
            next.acknowledge(offer.release.versionCode); save(next);
            if (!validOffer()) { save(previous); return false; }
            show.run(); return true;
        } catch (Exception ex) {
            if (previous != null && !storageFailed) {
                try { save(previous); } catch (HaokeUpdateVerifier.Failure ignored) { /* already fail closed */ }
            }
            offer = null; return false;
        }
    }
    synchronized boolean ignore() {
        if (!validOffer()) return false;
        try { HaokeUpdateState state = load(); state.ignoredVersionCode = offer.release.versionCode; save(state); offer = null; return true; }
        catch (HaokeUpdateVerifier.Failure ex) { offer = null; return false; }
    }
    synchronized boolean later() {
        if (!validOffer()) return false;
        try { HaokeUpdateState state = load(); state.later(offer.release.versionCode, clock.now()); save(state); offer = null; return true; }
        catch (HaokeUpdateVerifier.Failure ex) { offer = null; return false; }
    }
    synchronized String pageUrlFromUserGesture() { return validOffer() ? offer.release.pageUrl : null; }
}
