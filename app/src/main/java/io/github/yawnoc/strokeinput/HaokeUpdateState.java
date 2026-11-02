package io.github.yawnoc.strokeinput;

import org.json.JSONException;
import org.json.JSONObject;

/** Small persisted policy, isolated by product/platform/package/channel/certificate. */
final class HaokeUpdateState {
    static final long DEBOUNCE_MS = 60_000L;
    final String scope;
    HaokeUpdateVerifier.Checkpoint checkpoint;
    long lastCheckAt = -1;
    long promptedVersionCode = -1;
    long ignoredVersionCode = -1;
    long snoozedVersionCode = -1;
    long snoozedUntil;

    HaokeUpdateState(String scope) { this.scope = scope; }
    static HaokeUpdateState decode(String json, String scope) throws HaokeUpdateVerifier.Failure {
        HaokeUpdateState state = new HaokeUpdateState(scope);
        if (json == null) return state;
        try {
            JSONObject value = new JSONObject(json);
            HaokeUpdateVerifier.require(scope.equals(HaokeUpdateVerifier.string(value, "scope")), "INVALID_STORED_STATE");
            state.lastCheckAt = optional(value, "lastCheckAt");
            // Scheduling migrations never clear checkpoint or explicit reminder choices.
            state.promptedVersionCode = optional(value, "promptedVersionCode");
            state.ignoredVersionCode = optional(value, "ignoredVersionCode");
            state.snoozedVersionCode = optional(value, "snoozedVersionCode");
            state.snoozedUntil = value.has("snoozedUntil") ? HaokeUpdateVerifier.integer(value, "snoozedUntil", 0) : 0;
            if (value.has("checkpoint") && !value.isNull("checkpoint")) {
                JSONObject checkpoint = value.getJSONObject("checkpoint");
                String digest = HaokeUpdateVerifier.string(checkpoint, "digest");
                HaokeUpdateVerifier.require(digest.matches("[a-f0-9]{64}"), "INVALID_STORED_STATE");
                state.checkpoint = new HaokeUpdateVerifier.Checkpoint(
                        HaokeUpdateVerifier.integer(checkpoint, "revision", 0), digest,
                        HaokeUpdateVerifier.integer(checkpoint, "maxVersionCode", 0));
            }
            return state;
        } catch (JSONException | ClassCastException ex) { throw new HaokeUpdateVerifier.Failure("INVALID_STORED_STATE"); }
    }
    private static long optional(JSONObject value, String key) throws JSONException, HaokeUpdateVerifier.Failure {
        return !value.has(key) || value.isNull(key) ? -1 : HaokeUpdateVerifier.integer(value, key, 0);
    }
    String encode() throws HaokeUpdateVerifier.Failure {
        try {
            JSONObject value = new JSONObject(); value.put("scope", scope);
            if (lastCheckAt >= 0) value.put("lastCheckAt", lastCheckAt);
            if (promptedVersionCode >= 0) value.put("promptedVersionCode", promptedVersionCode);
            if (ignoredVersionCode >= 0) value.put("ignoredVersionCode", ignoredVersionCode);
            if (snoozedVersionCode >= 0) value.put("snoozedVersionCode", snoozedVersionCode);
            value.put("snoozedUntil", snoozedUntil);
            if (checkpoint != null) {
                JSONObject saved = new JSONObject(); saved.put("revision", checkpoint.revision);
                saved.put("digest", checkpoint.digest); saved.put("maxVersionCode", checkpoint.maxVersionCode);
                value.put("checkpoint", saved);
            } else value.put("checkpoint", JSONObject.NULL);
            return value.toString();
        } catch (JSONException ex) { throw new HaokeUpdateVerifier.Failure("STORAGE_UNAVAILABLE"); }
    }
    long retryAfter(long now) {
        long elapsed = now - lastCheckAt;
        return lastCheckAt >= 0 && elapsed >= 0 && elapsed < DEBOUNCE_MS ? DEBOUNCE_MS - elapsed : 0;
    }
    boolean suppressed(long version, long now) {
        if (ignoredVersionCode == version) return true;
        if (snoozedVersionCode == -1 || snoozedVersionCode == version) {
            if (snoozedUntil > now) return true;
            if (snoozedVersionCode == version && snoozedUntil > 0) return false;
        }
        return promptedVersionCode == version;
    }
    void acknowledge(long version) { promptedVersionCode = version; snoozedVersionCode = -1; snoozedUntil = 0; }
    void later(long version, long now) { snoozedVersionCode = version; snoozedUntil = now + HaokeUpdateVerifier.DAY_MS; }
}
