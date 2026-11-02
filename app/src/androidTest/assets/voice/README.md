# Offline voice test fixtures

Source: Google FLEURS (Conneau et al., 2022), https://huggingface.co/datasets/google/fleurs
License: Creative Commons Attribution 4.0 International (CC BY 4.0), https://creativecommons.org/licenses/by/4.0/
Dataset paper: https://arxiv.org/abs/2205.12446

These two full recordings are test-only assets and must not ship in the application APK.
Adaptation: converted original 16 kHz mono FLOAT WAV to 16 kHz mono PCM_16 WAV. No trimming or transcript changes.
Assertions use words from the original TSV references, not ASR-generated hypotheses.

This is an integration smoke test, not a representative accuracy benchmark or a microphone/acoustic test.

## Files

- `mandarin.wav`: `cmn_hans_cn`, test split, original `11432051235656569325.wav`, sentence ID `2005`.
  Reference: 但鸟类还是有很多地方跟恐龙相似。
  Duration: 4.740 s.
  Original SHA-256: `b22540868e628906ec84226a574a11aee4659e8f34f08956e7b250ff6ee937ed`.
  PCM fixture SHA-256: `095debea1f7def6079c2e5bd56c0deeaf54def312516b61e293fd4b2b7396a04`.

- `cantonese.wav`: `yue_hant_hk`, test split, original `2333507645426608575.wav`, sentence ID `1728`.
  Reference: 甘藍汁會根據化學物質的酸鹼度變色。
  Duration: 4.380 s.
  Original SHA-256: `f3ed002c3fe94c472ec91d62ae11dd2a0e3a7fd742caee346bd5bd5b1ae95fdc`.
  PCM fixture SHA-256: `146e56fc42784aec9aa66be207a819aa333b32f72ba8b39ce72a96aacf718982`.

## Recorder integration test

`VoiceRecorderTest` requires the target app's `android.permission.RECORD_AUDIO` to be granted before the instrumentation run. The outer ADB runner must record the original grant state, grant only for this test, and restore that state in a `finally` step after instrumentation exits. Revocation inside the target process may terminate instrumentation, so the test does not change grants itself.

The recorder test opens the app in the foreground and captures two independent one-second sessions, keeping PCM in memory only. It verifies start/stop/restart, sample count and valid PCM, and writes only sample count/peak/RMS statistics to Logcat. Zero-amplitude audio is explicitly logged as `SILENT_UNVERIFIED`; even nonzero amplitude does not prove speech transcription through the host microphone.
