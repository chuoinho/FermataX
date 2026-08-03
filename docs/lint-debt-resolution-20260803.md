# Android Lint debt resolution — 2026-08-03

## Scope and baseline

The pre-change `depends/utils/build/reports/lint-results-debug.xml` contained exactly
18 errors and 21 warnings. Lint stopped at that library, so the application report had
not previously completed. Once the library errors were removed, the first complete app
analysis exposed a separate pre-existing backlog of 158 errors and 354 warnings. This
report keeps those two sets separate.

No lint baseline or `lint.xml` suppression was introduced. Every suppression below is
attached to the smallest safe source method or resource and includes its rationale.

## Original 18 errors

| Rule | Count | Locations/root cause | Resolution |
| --- | ---: | --- | --- |
| `MissingPermission` | 6 | `NetUtils`: connectivity APIs were used without the library manifest declaring `ACCESS_NETWORK_STATE`. | Declared the permission in the utils manifest. No runtime prompt is needed because this is a normal permission. |
| `MissingPermission` | 4 | `ActivityBase`, `DynamicModuleInstaller`, and the two notification paths in `HttpDownloadStatusListener`: Android 13 notification permission was neither declared nor checked at the binder call. | Declared `POST_NOTIFICATIONS`; each call now checks permission and also handles the revoke-between-check-and-call race. A denied notification no longer crashes or interrupts the underlying operation. |
| `NewApi` | 7 | `HttpFileDownloader`: one unguarded path-normalization fallback and the desugared `java.nio.file` atomic replacement path. | Replaced the fallback with the API-safe absolute path. Kept atomic `Files.move` and added a method-scoped, commented suppression because core-library desugaring is enabled for every subproject. |
| `NewApi` | 1 | `PatternCompat.group(Matcher,String)`: lint could not infer that the base implementation is instantiated only on API 26+, while the pre-26 subclass overrides it. | Added a method-scoped, commented suppression documenting that construction invariant. |

Total: **18/18 resolved**.

## Original 21 warnings

| Rule | Count | Location/root cause | Resolution |
| --- | ---: | --- | --- |
| `DefaultLocale` | 1 | `HttpFileDownloader.setCharset` uppercased protocol text with the device locale. | Uses `Locale.ROOT`. |
| `OldTargetApi` | 1 | `depends/utils/build.gradle` pinned target 35 while the repository target is 36. | Uses the shared `SDK_TARGET_VERSION`. |
| `WifiManagerPotentialLeak` | 1 | `NetUtils` obtained `WifiManager` from a caller context. | Obtains it from `getApplicationContext()`. |
| `DefaultUncaughtExceptionDelegation` | 1 | `ActivityDelegate` replaced the process handler and consumed crashes. | Captures/restores the previous handler and delegates after recording the crash. |
| `QueryPermissionsNeeded` | 3 | Utils launches document-tree, mail, and speech-recognition intents without package-visibility declarations. | Added one manifest `<queries>` entry for each intent. |
| `PrivateResource` | 1 | `DialogView` referenced Material's private single-choice layout. | Added and uses an owned equivalent layout resource. |
| `UseCompatTextViewDrawableApis` | 1 | `ListView` directly called the framework drawable tint API. | Uses `TextViewCompat`. |
| `TrustAllX509TrustManager` | 2 | Project `SecurityUtils.InsecureTrustManager` accepted every peer certificate. | Removed it. Outbound TLS uses the platform trust store; the test server does not install a permissive client trust manager. |
| `CustomX509TrustManager` | 1 | The same custom trust manager did not perform platform validation. | Removed with the trust-all implementation above. |
| `TrustAllX509TrustManager` | 3 | Bytecode in `bcpkix-jdk18on:1.84` (one) and `google-http-client:2.1.0` (two), not project source or a trust manager selected by these changes. | Explicitly deferred. The findings remain visible in the report; suppressing dependency bytecode would require a broad module/global suppression, which this task forbids. |
| `NotifyDataSetChanged` | 2 | `ListView` replaces/filter-sorts the complete collection; `PreferenceViewAdapter` replaces the complete preference hierarchy. | Kept full invalidation with method-scoped, commented suppressions because no stable item-level diff exists in either operation. |
| `ClickableViewAccessibility` | 4 | Two `PreferenceView` touch-listener methods; lint reports both listener installation and touch handling. The listeners return `false`, leaving native `EditText` click/accessibility behavior active. | Added method-scoped, commented suppressions to the two methods. |

Total: **18 fixed or narrowly justified; 3 dependency-bytecode warnings explicitly
deferred; 21/21 accounted for**.

## Additional errors exposed after utils passed

The first complete application run reported 158 pre-existing errors. They were also
resolved because CI's lint gate cannot be green otherwise:

| Rule | Count | Resolution |
| --- | ---: | --- |
| `NewApi` | 107 | Replaced Java APIs unavailable at minSdk 28 with real compatibility helpers/overloads (`failedFuture`, UTF-8 URL codec, stream copy/conversion, formatting, and `CharSequence` length checks). Split Android 30 exit-history work behind an explicit `@RequiresApi` helper called from an SDK guard. |
| `MissingTranslation` | 25 | These resources are executable voice-parser regex grammars, not UI strings. Added an individual, commented `tools:ignore` to each grammar so incomplete locales safely fall back to English. |
| `UseAppTint` | 11 | Replaced framework `android:tint` with AppCompat `app:tint`. |
| `RestrictedApi` | 9 | `PreviewProgram.Builder` is the required public TV-provider publication API but this dependency version marks its setters library-restricted. Added one method-scoped, commented suppression around the builder transaction. |
| Manifest correctness (`IntentFilterExportedReceiver`, `AppLinkUrlError`, `ImpliedTouchscreenHardware`, `MissingTvBanner`, `MissingClass`, `MissingLeanbackSupport`) | 6 | Made export/touchscreen/banner declarations explicit, scoped the non-HTTP local-document intent explanation, and documented the manifest-removal placeholder for the optional ML Kit provider. Auto deliberately removes the base Leanback launcher and enters through Android Auto, so `MissingLeanbackSupport` is suppressed only on that flavor manifest with this contract documented. |

The remaining app warnings (352 Auto / 351 Mobile in the isolated commit) are outside the original stable
21-warning scope and do not fail the configured lint gate. They remain visible for
future debt passes; none were hidden by a baseline.

## Verification

- `./gradlew :utils:lintDebug`: pass, 0 errors; only the 3 external dependency warnings above.
- `./gradlew :fermata:lintAutoDebug`: pass, 0 errors / 352 warnings.
- `./gradlew :fermata:lintMobileDebug`: pass, 0 errors / 351 warnings.
- `./gradlew lint`: pass.
- `./gradlew testMobileDebugUnitTest testAutoDebugUnitTest`: pass.

The CI lint step now names both application variant tasks explicitly; AGP's generic
`lint` lifecycle task selected only Auto in this project and therefore did not prove
Mobile lint on the runner.

The architecture guard, whitespace check, commit/push, and real GitHub Actions result
are recorded in the delivery report for the commit containing this document.
