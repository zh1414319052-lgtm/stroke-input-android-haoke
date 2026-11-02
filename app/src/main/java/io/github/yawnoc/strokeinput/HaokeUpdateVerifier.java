package io.github.yawnoc.strokeinput;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.util.encoders.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Android port of the Haoke v2 verifier. No network, UI or mutable trust state. */
final class HaokeUpdateVerifier {
    static final int MAX_ENVELOPE = 65_536;
    static final int MAX_PAYLOAD = 32_768;
    static final long DAY_MS = 86_400_000L;
    private static final String ID = "[A-Za-z0-9][A-Za-z0-9_-]{0,79}";
    private static final String HEX = "[a-f0-9]{64}";
    private static final Set<String> ABIS = new HashSet<>(Arrays.asList(
            "arm64-v8a", "armeabi-v7a", "x86", "x86_64"));

    static final class Failure extends Exception {
        final String code;
        Failure(String code) { super(code); this.code = code; }
    }

    static final class Config {
        final String softwareId, applicationId, channel, certificateSha256;
        final Map<String, String> publicKeys;
        Config(String softwareId, String applicationId, String channel,
               String certificateSha256, Map<String, String> publicKeys) {
            this.softwareId = softwareId;
            this.applicationId = applicationId;
            this.channel = channel;
            this.certificateSha256 = certificateSha256;
            this.publicKeys = Collections.unmodifiableMap(new HashMap<>(publicKeys));
        }
        String feedUrl() {
            return "https://haoke.org/api/client-updates/v2/Android/" + applicationId + "/" + channel;
        }
        String pageUrl(String releaseId) {
            return "https://haoke.org/software/" + softwareId + "/releases/v2/Android/" + channel + "/" + releaseId;
        }
        String actionUrl(String releaseId) {
            return "https://haoke.org/api/client-updates/v2/releases/" + softwareId + "/Android/" + channel + "/" + releaseId + "/download";
        }
        String scope() { return softwareId + ":Android:" + applicationId + ":" + channel + ":" + certificateSha256; }
    }

    static final class Device {
        final long versionCode;
        final int sdk;
        final String architecture;
        Device(long versionCode, int sdk, String architecture) {
            this.versionCode = versionCode; this.sdk = sdk; this.architecture = architecture;
        }
    }

    static final class Checkpoint {
        final long revision, maxVersionCode;
        final String digest;
        Checkpoint(long revision, String digest, long maxVersionCode) {
            this.revision = revision; this.digest = digest; this.maxVersionCode = maxVersionCode;
        }
    }

    static final class Release {
        public final long versionCode;
        public final String versionName, notesZh, notesEn, pageUrl;
        Release(long versionCode, String versionName, String notesZh, String notesEn, String pageUrl) {
            this.versionCode = versionCode; this.versionName = versionName;
            this.notesZh = notesZh; this.notesEn = notesEn; this.pageUrl = pageUrl;
        }
    }

    static final class Receipt {
        final String availability;
        final Release release;
        final Checkpoint checkpoint;
        final long expiresAt;
        Receipt(String availability, Release release, Checkpoint checkpoint, long expiresAt) {
            this.availability = availability; this.release = release;
            this.checkpoint = checkpoint; this.expiresAt = expiresAt;
        }
    }

    static void validateConfig(Config config, Device device) throws Failure {
        require(config.softwareId != null && config.softwareId.matches(ID)
                && config.applicationId != null && config.applicationId.length() <= 128
                && config.applicationId.matches("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+")
                && ("stable".equals(config.channel) || "qa".equals(config.channel))
                && config.certificateSha256 != null && config.certificateSha256.matches(HEX), "INVALID_CONFIGURATION");
        require(!config.publicKeys.isEmpty(), "MISSING_PIN");
        for (Map.Entry<String, String> entry : config.publicKeys.entrySet()) {
            require(entry.getKey() != null && !entry.getKey().isEmpty(), "MISSING_PIN");
            decode64(entry.getValue(), 32, 32);
        }
        require(device.versionCode >= 0 && device.versionCode <= 9_007_199_254_740_991L
                && device.sdk > 0 && device.sdk <= 999 && ABIS.contains(device.architecture), "INVALID_DEVICE");
    }

    static Receipt verify(byte[] envelopeBytes, Config config, Device device, Checkpoint previous, long now)
            throws Failure {
        validateConfig(config, device);
        require(envelopeBytes != null && envelopeBytes.length <= MAX_ENVELOPE, "INVALID_ENVELOPE");
        try {
            JSONObject envelope = new JSONObject(utf8(envelopeBytes));
            keys(envelope, "algorithm", "keyId", "payload", "signature");
            String keyId = string(envelope, "keyId");
            require("Ed25519".equals(string(envelope, "algorithm")) && config.publicKeys.containsKey(keyId), "UNTRUSTED_KEY");
            byte[] payloadBytes = decode64(string(envelope, "payload"), 0, MAX_PAYLOAD);
            byte[] signature = decode64(string(envelope, "signature"), 64, 64);
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, new Ed25519PublicKeyParameters(decode64(config.publicKeys.get(keyId), 32, 32), 0));
            verifier.update(payloadBytes, 0, payloadBytes.length);
            require(verifier.verifySignature(signature), "INVALID_SIGNATURE");
            // Interpret fields only after verifying the exact received payload bytes.
            JSONObject payload = new JSONObject(utf8(payloadBytes));
            keys(payload, "schemaVersion", "platform", "softwareId", "applicationId", "channel", "identity",
                    "revision", "issuedAt", "expiresAt", "status", "release");
            require(integer(payload, "schemaVersion", 0) == 2, "UNKNOWN_SCHEMA");
            require("Android".equals(string(payload, "platform"))
                    && config.softwareId.equals(string(payload, "softwareId"))
                    && config.applicationId.equals(string(payload, "applicationId"))
                    && config.channel.equals(string(payload, "channel")), "IDENTITY_MISMATCH");
            JSONObject identity = payload.getJSONObject("identity");
            keys(identity, "kind", "certificateSha256");
            require("android".equals(string(identity, "kind"))
                    && config.certificateSha256.equals(string(identity, "certificateSha256")), "IDENTITY_MISMATCH");
            long revision = integer(payload, "revision", 0);
            long issued = time(string(payload, "issuedAt")), expires = time(string(payload, "expiresAt"));
            require(now >= 0 && issued <= now + 300_000L && expires > now && expires > issued
                    && expires - issued <= 2 * DAY_MS, "EXPIRED_OR_FUTURE");
            String status = string(payload, "status");
            Release release = null;
            String availability;
            if ("none".equals(status) || "withdrawn".equals(status)) {
                require(payload.isNull("release") && ("none".equals(status) ? revision == 0 : revision > 0), "INVALID_STATE");
                availability = status;
            } else {
                require("published".equals(status) && revision > 0, "INVALID_STATE");
                JSONObject value = payload.getJSONObject("release");
                keys(value, "releaseId", "softwareId", "versionCode", "versionName", "publishedAt", "notes", "pageUrl", "artifact");
                String releaseId = string(value, "releaseId");
                require(releaseId.matches(ID) && config.softwareId.equals(string(value, "softwareId")), "INVALID_RELEASE");
                long version = integer(value, "versionCode", 1);
                String name = string(value, "versionName");
                require(plain(name, 80) && !name.trim().isEmpty(), "INVALID_RELEASE");
                require(time(string(value, "publishedAt")) <= issued + 300_000L, "INVALID_TIME");
                JSONObject notes = value.getJSONObject("notes");
                keys(notes, "zh", "en");
                String zh = string(notes, "zh"), en = string(notes, "en");
                require(plain(zh, 4000) && plain(en, 4000), "INVALID_NOTES");
                JSONObject artifact = value.getJSONObject("artifact");
                keys(artifact, "distribution", "sha256", "sizeBytes", "minOS", "architectures", "actionUrl");
                require(config.pageUrl(releaseId).equals(string(value, "pageUrl"))
                        && config.actionUrl(releaseId).equals(string(artifact, "actionUrl")), "UNTRUSTED_URL");
                require("direct".equals(string(artifact, "distribution")), "DISTRIBUTION_MISMATCH");
                require(string(artifact, "sha256").matches(HEX) && integer(artifact, "sizeBytes", 1) > 0, "INVALID_ARTIFACT");
                String minimum = string(artifact, "minOS");
                require(minimum.matches("[0-9]{1,3}"), "INVALID_OS_VERSION");
                JSONArray abis = artifact.getJSONArray("architectures");
                Set<String> seen = new HashSet<>();
                require(abis.length() > 0, "INVALID_ARCHITECTURE");
                for (int i = 0; i < abis.length(); i++) {
                    Object abi = abis.get(i);
                    require(abi instanceof String && ABIS.contains(abi) && seen.add((String) abi), "INVALID_ARCHITECTURE");
                }
                availability = version <= device.versionCode ? "current"
                        : !seen.contains(device.architecture) || device.sdk < Integer.parseInt(minimum) ? "incompatible" : "available";
                release = new Release(version, name, zh, en, config.pageUrl(releaseId));
            }
            payload.remove("issuedAt"); payload.remove("expiresAt");
            Checkpoint current = new Checkpoint(revision, sha256(canonical(payload).getBytes(StandardCharsets.UTF_8)),
                    release == null ? 0 : release.versionCode);
            return new Receipt(availability, release, acceptCheckpoint(current, previous), expires);
        } catch (Failure ex) {
            throw ex;
        } catch (JSONException | IllegalArgumentException ex) {
            throw new Failure("INVALID_PAYLOAD");
        }
    }

    static Checkpoint acceptCheckpoint(Checkpoint current, Checkpoint previous) throws Failure {
        if (previous != null) {
            require(previous.revision >= 0 && previous.maxVersionCode >= 0 && previous.digest.matches(HEX), "INVALID_CHECKPOINT");
            require(current.revision >= previous.revision, "REVISION_ROLLBACK");
            require(current.revision != previous.revision || current.digest.equals(previous.digest), "REVISION_CONTENT_CHANGED");
            require(current.maxVersionCode == 0 || current.maxVersionCode >= previous.maxVersionCode, "VERSION_ROLLBACK");
        }
        return new Checkpoint(current.revision, current.digest,
                Math.max(current.maxVersionCode, previous == null ? 0 : previous.maxVersionCode));
    }

    static String utf8(byte[] bytes) throws Failure {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) { throw new Failure("INVALID_UTF8"); }
    }
    static byte[] decode64(String value, int length, int max) throws Failure {
        require(value != null && value.length() <= ((max + 2) / 3) * 4
                && value.matches("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?"), "INVALID_BASE64");
        try {
            byte[] bytes = Base64.decode(value);
            require(bytes.length <= max && (length == 0 || bytes.length == length)
                    && Base64.toBase64String(bytes).equals(value), "INVALID_BASE64");
            return bytes;
        } catch (IllegalArgumentException ex) { throw new Failure("INVALID_BASE64"); }
    }
    static String sha256(byte[] bytes) throws Failure {
        try {
            StringBuilder out = new StringBuilder(64);
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format(Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException ex) { throw new Failure("CRYPTO_UNAVAILABLE"); }
    }
    static long time(String value) throws Failure {
        require(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z"), "INVALID_TIME");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC")); format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(value, position);
        require(parsed != null && position.getIndex() == value.length() && format.format(parsed).equals(value), "INVALID_TIME");
        return parsed.getTime();
    }
    static String canonical(Object value) throws JSONException, Failure {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> names = new ArrayList<>(); object.keys().forEachRemaining(names::add);
            Collections.sort(names); StringBuilder out = new StringBuilder("{");
            for (String name : names) { if (out.length() > 1) out.append(','); out.append(JSONObject.quote(name)).append(':').append(canonical(object.get(name))); }
            return out.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value; StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) { if (i > 0) out.append(','); out.append(canonical(array.get(i))); }
            return out.append(']').toString();
        }
        if (value instanceof String) return JSONObject.quote((String) value);
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) return value.toString();
        throw new Failure("INVALID_NUMBER");
    }
    private static boolean plain(String value, int max) {
        return value.length() <= max && !value.matches("(?s).*[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f<>].*");
    }
    static String string(JSONObject object, String key) throws JSONException, Failure {
        Object value = object.get(key); require(value instanceof String, "INVALID_FIELDS"); return (String) value;
    }
    static long integer(JSONObject object, String key, long minimum) throws JSONException, Failure {
        Object value = object.get(key);
        require(value instanceof Integer || value instanceof Long, "INVALID_INTEGER");
        long number = ((Number) value).longValue();
        require(number >= minimum && number <= 9_007_199_254_740_991L, "INVALID_INTEGER"); return number;
    }
    private static void keys(JSONObject object, String... expected) throws Failure {
        Set<String> actual = new HashSet<>(); Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) actual.add(iterator.next());
        require(actual.equals(new HashSet<>(Arrays.asList(expected))), "INVALID_FIELDS");
    }
    static void require(boolean condition, String code) throws Failure { if (!condition) throw new Failure(code); }
}
