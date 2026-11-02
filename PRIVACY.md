# Privacy Policy (English version)

**Input, handwriting and voice recognition are processed on this device. The
Haoke edition connects to the Haoke website only to check version updates.**

## Haoke formal build: offline input (2026-10-29)

The Haoke build requests microphone access only when you choose voice input.
Speech recognition runs locally with the model bundled in the APK. Audio is held
in memory for one recording (at most 30 seconds); the app does not save audio
files or upload recordings, recognition results, or handwriting. Recognised text
is inserted into the text field you are using. Leaving that input session or
cancelling a recording discards it. Microphone access can be revoked in Android
settings. ONNX Runtime telemetry remains disabled. These changes are maintained
in the accompanying Haoke source tree;
the upstream repository and contact below refer to the original project.

## Version checks

The Internet permission is used only for HTTPS requests to the fixed Haoke
signed-update feed. Checks run while the settings screen is in the foreground
and no text input, keyboard or other dialog is active, or when you tap
“Check for Haoke updates”. Leaving the settings screen cancels the request.
There is no background polling, account, analytics, device identifier, automatic
download or automatic installation. No typed text, quick phrases, handwriting,
audio or recognition result is included in an update request. Normal connection
metadata, including your IP address, is necessarily visible to the website.

Update versions, integrity checkpoints and your reminder choices are kept in
this app's private settings on the device. “Later” reminds after 24 hours;
“Ignore this version” suppresses that version. Manual checks can show it again.
Release information must pass signature and app-identity checks before display.
Only tapping “View version” opens the version page in your external browser;
that browser and any download website then apply their own privacy policies.
The privacy, help and about pages bundled with this app do not load remote
resources. Other source-code or website links open only when you tap them.

You are welcome and encouraged to inspect the source code at
<<https://github.com/stroke-input/stroke-input-android>>.
Should you find any vulnerabilities or privacy concerns,
please report to <<yawnocdev@pm.me>>.

---

[私隱政策（中文版）](PRIVACY-zh.md#私隱政策中文版) <br>
(If there be any discrepancy between the English and the Chinese,
the English version shall prevail.)
