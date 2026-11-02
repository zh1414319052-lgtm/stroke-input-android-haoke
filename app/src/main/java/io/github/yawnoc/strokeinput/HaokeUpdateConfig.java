package io.github.yawnoc.strokeinput;

/** Public release identities verified against the Haoke administrator API on 2026-10-29. */
final class HaokeUpdateConfig {
    private HaokeUpdateConfig() {}

    static HaokeUpdateVerifier.Config create() {
        return new HaokeUpdateVerifier.Config(
                "abc5c0fe8b0eab06", "io.github.yawnoc.strokeinput.haoke", "stable",
                "61dcd13391bcdd7025083cabd3095c85638c02cacb56a72653c6bc09acec8cb9",
                java.util.Collections.singletonMap("haoke-updates-000ef7a45a5a58d5",
                        "+8cSV1C+t+6GipgPi3a2zjZ4T3W/Z+tZDKzzO4tQXOY="));
    }
}
