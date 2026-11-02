package io.github.yawnoc.strokeinput;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.util.encoders.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.*;

/** Public server fixture plus in-memory, test-only signing keys. No production signing. */
public class HaokeUpdateVerifierTest {
    private JSONObject fixture;
    private HaokeUpdateVerifier.Config config;
    private HaokeUpdateVerifier.Device device;
    private long now;
    private Ed25519PrivateKeyParameters key;

    @Before public void setup() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/updates/server-android-v2.json")) {
            assertNotNull(input); ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int length;
            while ((length = input.read(buffer)) != -1) out.write(buffer, 0, length);
            fixture = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8).replace("\uFEFF", ""));
        }
        now = fixture.getLong("testNow");
        config = new HaokeUpdateVerifier.Config(fixture.getString("softwareId"), fixture.getString("applicationId"), "stable",
                fixture.getJSONObject("identities").getJSONObject("Android").getString("certificateSha256"),
                Collections.singletonMap(fixture.getString("keyId"), fixture.getString("publicKeyBase64")));
        device = new HaokeUpdateVerifier.Device(1, 35, "arm64-v8a");
        key = new Ed25519PrivateKeyParameters(new SecureRandom());
    }
    private JSONObject fixtureValue(String phase) throws Exception {
        JSONArray values = fixture.getJSONArray("fixtures");
        for (int i = 0; i < values.length(); i++) if (phase.equals(values.getJSONObject(i).getString("phase"))) return values.getJSONObject(i);
        throw new AssertionError(phase);
    }
    private byte[] envelope(String phase) throws Exception { return fixtureValue(phase).getJSONObject("envelope").toString().getBytes(StandardCharsets.UTF_8); }
    private JSONObject payload(String phase) throws Exception { return new JSONObject(fixtureValue(phase).getJSONObject("payload").toString()); }
    private byte[] signed(JSONObject payload) throws Exception {
        config = new HaokeUpdateVerifier.Config(config.softwareId, config.applicationId, config.channel, config.certificateSha256,
                Collections.singletonMap("local-unit-test", Base64.toBase64String(key.generatePublicKey().getEncoded())));
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        Ed25519Signer signer = new Ed25519Signer(); signer.init(true, key); signer.update(bytes, 0, bytes.length);
        JSONObject envelope = new JSONObject(); envelope.put("algorithm", "Ed25519"); envelope.put("keyId", "local-unit-test");
        envelope.put("payload", Base64.toBase64String(bytes)); envelope.put("signature", Base64.toBase64String(signer.generateSignature()));
        return envelope.toString().getBytes(StandardCharsets.UTF_8);
    }
    private HaokeUpdateVerifier.Receipt verify(byte[] bytes, HaokeUpdateVerifier.Checkpoint previous) throws Exception {
        return HaokeUpdateVerifier.verify(bytes, config, device, previous, now);
    }
    private void rejects(String code, byte[] bytes) throws Exception {
        try { verify(bytes, null); fail("accepted " + code); }
        catch (HaokeUpdateVerifier.Failure failure) { assertEquals(code, failure.code); }
    }
    private String date(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC")); return format.format(new Date(millis));
    }

    @Test public void actualServerAndroidNonePublishedWithdrawnInteroperate() throws Exception {
        HaokeUpdateVerifier.Receipt none = verify(envelope("none"), null);
        assertEquals("none", none.availability); assertNull(none.release);
        HaokeUpdateVerifier.Receipt published = verify(envelope("published"), none.checkpoint);
        assertEquals("available", published.availability); assertNotNull(published.release);
        HaokeUpdateVerifier.Receipt withdrawn = verify(envelope("withdrawn"), published.checkpoint);
        assertEquals("withdrawn", withdrawn.availability); assertNull(withdrawn.release);
        assertEquals(published.release.versionCode, withdrawn.checkpoint.maxVersionCode);
    }
    @Test public void changedBytesAndUnknownKeysFailBeforePayloadUse() throws Exception {
        JSONObject value = fixtureValue("published").getJSONObject("envelope");
        JSONObject changed = new JSONObject(value.toString());
        byte[] bytes = Base64.decode(changed.getString("payload")); bytes[5] ^= 1;
        changed.put("payload", Base64.toBase64String(bytes));
        rejects("INVALID_SIGNATURE", changed.toString().getBytes(StandardCharsets.UTF_8));
        changed = new JSONObject(value.toString()); changed.put("keyId", "response-supplied-key");
        rejects("UNTRUSTED_KEY", changed.toString().getBytes(StandardCharsets.UTF_8));
    }
    @Test public void wrongSoftwareAppPlatformChannelOrCertificateAreRejected() throws Exception {
        for (String field : new String[]{"softwareId", "applicationId", "platform", "channel"}) {
            JSONObject value = payload("published"); value.put(field, "other"); rejects("IDENTITY_MISMATCH", signed(value));
        }
        JSONObject value = payload("published"); value.getJSONObject("identity").put("certificateSha256", "b".repeat(64));
        rejects("IDENTITY_MISMATCH", signed(value));
    }
    @Test public void expiryFutureTimesAndExcessLifetimeAreRejected() throws Exception {
        JSONObject value = payload("published"); value.put("expiresAt", date(now)); rejects("EXPIRED_OR_FUTURE", signed(value));
        value = payload("published"); value.put("issuedAt", date(now + 300_001)); rejects("EXPIRED_OR_FUTURE", signed(value));
        value = payload("published"); value.put("expiresAt", date(now + 2 * HaokeUpdateVerifier.DAY_MS + 1)); rejects("EXPIRED_OR_FUTURE", signed(value));
        value = payload("published"); value.put("expiresAt", "2026-02-30T00:00:00.000Z"); rejects("INVALID_TIME", signed(value));
    }
    @Test public void validRenewalPreservesBusinessDigestButChangedRevisionContentFails() throws Exception {
        HaokeUpdateVerifier.Receipt original = verify(envelope("published"), null);
        JSONObject renewed = payload("published"); renewed.put("issuedAt", date(now + 1000)); renewed.put("expiresAt", date(now + HaokeUpdateVerifier.DAY_MS + 1000));
        assertEquals(original.checkpoint.digest, verify(signed(renewed), original.checkpoint).checkpoint.digest);
        renewed.getJSONObject("release").getJSONObject("notes").put("en", "changed notes");
        try { verify(signed(renewed), original.checkpoint); fail(); }
        catch (HaokeUpdateVerifier.Failure failure) { assertEquals("REVISION_CONTENT_CHANGED", failure.code); }
    }
    @Test public void rollbackIsRejectedIncludingAfterWithdrawal() throws Exception {
        HaokeUpdateVerifier.Receipt published = verify(envelope("published"), null);
        try { verify(envelope("none"), published.checkpoint); fail(); }
        catch (HaokeUpdateVerifier.Failure failure) { assertEquals("REVISION_ROLLBACK", failure.code); }
        HaokeUpdateVerifier.Receipt withdrawn = verify(envelope("withdrawn"), published.checkpoint);
        JSONObject value = payload("published"); value.put("revision", withdrawn.checkpoint.revision + 1);
        value.getJSONObject("release").put("versionCode", published.release.versionCode - 1);
        try { verify(signed(value), withdrawn.checkpoint); fail(); }
        catch (HaokeUpdateVerifier.Failure failure) { assertEquals("VERSION_ROLLBACK", failure.code); }
    }
    @Test public void signedArbitraryLinksAndUnsafeNotesAreRejected() throws Exception {
        JSONObject value = payload("published"); value.getJSONObject("release").put("pageUrl", "https://example.com/");
        rejects("UNTRUSTED_URL", signed(value));
        value = payload("published"); value.getJSONObject("release").getJSONObject("artifact").put("actionUrl", config.actionUrl("other"));
        rejects("UNTRUSTED_URL", signed(value));
        value = payload("published"); value.getJSONObject("release").getJSONObject("notes").put("zh", "<script>text</script>");
        rejects("INVALID_NOTES", signed(value));
    }
    @Test public void compatibilityAndCurrentVersionDoNotOfferAnUpdate() throws Exception {
        JSONObject value = payload("published"); value.getJSONObject("release").getJSONObject("artifact").put("minOS", "99");
        assertEquals("incompatible", verify(signed(value), null).availability);
        value = payload("published"); value.getJSONObject("release").getJSONObject("artifact").put("architectures", new JSONArray().put("x86_64"));
        assertEquals("incompatible", verify(signed(value), null).availability);
        device = new HaokeUpdateVerifier.Device(9_999_999, 35, "arm64-v8a");
        assertEquals("current", verify(signed(payload("published")), null).availability);
    }
    @Test public void strictSchemaBase64AndSizeBounds() throws Exception {
        JSONObject value = payload("published"); value.put("newField", true); rejects("INVALID_FIELDS", signed(value));
        value = payload("published"); value.put("schemaVersion", 3); rejects("UNKNOWN_SCHEMA", signed(value));
        value = payload("published"); value.put("revision", "1"); rejects("INVALID_INTEGER", signed(value));
        JSONObject envelope = new JSONObject(new String(signed(payload("published")), StandardCharsets.UTF_8));
        envelope.put("signature", envelope.getString("signature").replace("=", ""));
        rejects("INVALID_BASE64", envelope.toString().getBytes(StandardCharsets.UTF_8));
        rejects("INVALID_ENVELOPE", new byte[65_537]);
    }
}
