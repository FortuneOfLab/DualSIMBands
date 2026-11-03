# Dependencies and references

- `dev.rikka.shizuku:api:13.1.5`, `provider:13.1.5`, `shared:13.1.5`, `aidl:13.1.5`: Shizuku API, MIT. https://github.com/RikkaApps/Shizuku-API
- `org.lsposed.hiddenapibypass:hiddenapibypass:6.1`: AndroidHiddenApiBypass, Apache-2.0. https://github.com/LSPosed/AndroidHiddenApiBypass
- `androidx.appcompat:appcompat:1.8.0` and its AndroidX dependencies (fragment, activity, core, annotation, …): Apache-2.0. https://android.googlesource.com/platform/frameworks/support/
- `com.google.android.material:material:1.14.0`: Material Components for Android, Apache-2.0. https://github.com/material-components/material-components-android
- Material Symbols `sim_card` and `sim_card_download` (the SIM-kind icons, as vector drawables `ic_sim_physical` and `ic_sim_esim`): Google, Apache-2.0. https://github.com/google/material-design-icons
- JUnit 4.13.2 (EPL-1.0), Hamcrest Core 1.3 (BSD-3-Clause): development tests only, not packaged in the APK.
- Gradle wrapper scripts and wrapper JAR: Gradle project, Apache-2.0. https://github.com/gradle/gradle

The SDK Platform/Build Tools are build-time prerequisites and are not bundled.
The application source is an independent, narrow implementation using Android platform APIs; Pixel IMS was consulted as a reference for current instrumentation-based permission delegation and nonpersistent carrier overrides.
The published documentation of Hidden Band Info (MIT) was read for its approach to listening for PhysicalChannelConfig through Shizuku; no code was copied. NewRadioSupporter was consulted for ideas only (no licence; no code copied).
The Apache-2.0 licence text in `licenses/HiddenApiBypass-Apache-2.0.txt` also covers the AndroidX and Material libraries.
