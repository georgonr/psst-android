# psst — Element remnants (D94)

Every place where "Element", "Element X", "ElementX" or the Element logo could reach the user, found by searching
`values/` + `values-sk/` resources of all modules, all `AndroidManifest.xml` labels, string literals in Kotlin
(`"...Element..."`), and the `element*` drawables. Other locales (de, fr, …) are not handled: psst ships for SK/EN.

Upstream strings are overridden in the **app module** (`app/src/main/res/values/psst_overrides.xml` and
`app/src/main/res/values-sk/psst_overrides.xml`). App resources win over library resources at merge time, so a
Localazy sync of the libraries does not bring the wording back and the library files stay untouched.

## Changed

| Where (upstream definition) | Before → After |
|---|---|
| `features/call/impl/src/main/AndroidManifest.xml:34` label `@string/element_call` (`features/call/impl/src/main/res/values/do_not_translate.xml:9`) — call activity in recents | "Element Call" → "psst" |
| `features/call/impl/src/main/res/values/localazy.xml:7` `screen_incoming_call_subtitle_android` | "Incoming Element Call" → "Incoming psst call" |
| `features/call/impl/src/main/res/values-sk/translations.xml:7` | "Prichádzajúci hovor Element Call" → "Prichádzajúci hovor psst" |
| `features/call/impl/src/main/res/values/localazy.xml:6` `call_invalid_audio_device_bluetooth_devices_disabled` | "Element Call does not support using Bluetooth audio devices …" → "psst calls do not support Bluetooth audio devices in this Android version. …" |
| `features/call/impl/src/main/res/values-sk/translations.xml:6` | "Element Call nepodporuje používanie zvukových zariadení Bluetooth …" → "Hovory psst v tejto verzii systému Android nepodporujú zvukové zariadenia Bluetooth. …" |
| `features/messages/impl/src/main/res/values/localazy.xml:114` `screen_room_timeline_legacy_call` | "… use the new Element X app." → "Unsupported call. Ask if the caller can use the psst app." |
| `features/messages/impl/src/main/res/values-sk/translations.xml:84` | "… novú aplikáciu Element X." → "Nepodporovaný hovor. Opýtajte sa, či volajúci môže použiť aplikáciu psst." |
| `features/preferences/impl/src/main/res/values/localazy.xml:96` `screen_notification_settings_sound_element_default` (no SK upstream) | "Element Default" → EN "psst (default)", SK "psst (predvolený)" |
| `features/preferences/impl/src/main/res/values/localazy.xml:97` `screen_notification_settings_sound_element_fade` (no SK upstream) | "Element Fade" → EN "psst (soft)", SK "psst (stíšený)" |
| `features/preferences/impl/src/main/res/values/localazy.xml:8` `screen_advanced_settings_element_call_base_url` | "Custom Element Call base URL" → "Custom call base URL" |
| `features/preferences/impl/src/main/res/values-sk/translations.xml:8` | "Vlastná Element Call základná URL adresa" → "Vlastná základná URL adresa hovorov" |
| `features/preferences/impl/src/main/res/values/localazy.xml:9` `…_base_url_description` | "Set a custom base URL for Element Call." → "Set a custom base URL for calls." |
| `features/preferences/impl/src/main/res/values-sk/translations.xml:9` | "Nastaviť vlastnú základnú URL adresu pre Element Call." → "Nastaviť vlastnú základnú URL adresu pre hovory." |
| `libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/auth/RustMatrixAuthenticationService.kt:160` `initialDeviceName` (password login; device list seen by the user and other clients) | "Element X Android" → "psst Android" |
| `features/rageshake/impl/src/main/kotlin/io/element/android/features/rageshake/impl/crash/VectorUncaughtExceptionHandler.kt:42` crash report header | "ElementX" → "psst" |

## Kept

| Where | Why |
|---|---|
| `libraries/designsystem/src/main/res/drawable*-xxhdpi/element_logo.png` (`ElementLogoAtom`, used only by `OnBoardingContent`) | unreachable: `app/src/main/res/drawable/onboarding_logo.xml` exists, so the onboarding screen shows the psst logo (`OnBoardingLogo`) instead. |
| `features/login/impl/src/main/res/drawable-xxhdpi/element_pro_logo.png`, `element_foss_logo.png`, strings `screen_onboarding_welcome_title` ("Be in your element"), `screen_missing_key_backup_open_element_classic`, `screen_missing_key_backup_step_1` (`features/login/impl/src/main/res/values/localazy.xml:41,42,58`) | Element Classic migration flow (`LoginWithClassicView`, `MissingKeyBackupView`): reachable only when the Element Classic app (`im.vector.app`) is installed and its exported session service answers psst — it names that third-party app. Practically unreachable; kept. |
| `screen_change_server_error_element_pro_required_*` (`features/login/impl/src/main/res/values/localazy.xml:11,12`, `values-sk/translations.xml:10,11`) | unreachable: shown only when a homeserver's enterprise config sets `enforce_element_pro`; psst servers do not. |
| `features/preferences/impl/.../developer/appsettings/AppDeveloperSettingsView.kt:138` category "Element Call" | developer options only (hidden). |
| `features/login/impl/.../classic/ElementClassicConnection.kt:314` "The homeserver is not compatible with Element X" | Element Classic flow (see above), error text of an unreachable state. |
| `app/src/main/kotlin/io/element/android/x/info/Logs.kt:25` "Element X: https://github.com/element-hq/…/commit/…" | logcat/log file only (attribution of the upstream revision). |
| `appconfig/.../LearnMoreConfig.kt:12-15` "Learn more" links to element.io/help | not "Element" text in the app, but the pages are Element-branded. **Open item for the lead**: psst has no help pages for encryption/verification/backup yet. |
| `*PreviewParam.kt`, `*Preview*.kt` literals ("Element X", "Element") | Compose previews / screenshot tests only, not in the app. |
| Theme / class / package names (`Theme.ElementX`, `ElementXApplication`, `io.element.android.*`) | not visible (app label is the literal "psst", D83). |
| About screen license text "modified version of Element …" | legal (kept on purpose, spec §4). |
| Other locales (`values-de/`, `values-fr/`, …) | not handled, psst ships SK/EN. |
