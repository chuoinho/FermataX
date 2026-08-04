# Generated-artifact cleanup — 2026-08-04

## Finding

No native-build cache, Gradle output, IDE state, local configuration, signing material, APK/AAB,
or archive is tracked at the current HEAD. A full tracked-file scan and an all-history object-name
scan found no tracked `.cxx/`, `.externalNativeBuild/`, `build/`, CMake cache, or Ninja output.
Therefore no `git rm --cached` operation or history rewrite is required.

The first isolated Windows clone also exposed two tracked blobs whose stored line endings had not
been normalized after `.gitattributes` was introduced. That made a brand-new checkout immediately
appear dirty. `theme_black_starwars.xml` and `gradlew.bat` were renormalized with no semantic diff;
future checkouts now receive LF and CRLF respectively, as their existing attributes require.

The earlier `.cxx` statistics incident was not caused by a missing ignore entry: `.cxx/` had been
ignored since 2025. It was caused by a raw recursive filesystem count that included ignored native
output. The local `modules/whisper/.cxx/` cache was removed, and the canonical workspace rules now
require source statistics to use tracked files or explicit source roots.

## Artifact inventory and resolution

| Category | Tracking state | Resolution |
| --- | --- | --- |
| `modules/whisper/.cxx/`, including CMake/Ninja files and downloaded whisper.cpp trees | Local, ignored, never tracked | Removed locally; `.cxx/` remains ignored |
| Module/root `build/` directories and root `.gradle/` | Local, ignored, never tracked | Kept as normal reproducible Gradle output; absent from a clean checkout |
| `.codex-adb/` and `.tools/` | Local, ignored tool workspaces, never tracked | `.codex-adb/` preserved; `.tools/` cache removed when disposing the isolated verification checkout. Neither is a build input |
| `.externalNativeBuild/`, CMake cache/Ninja/compile database, `cmake-build-*` | Not currently present or tracked | Added/confirmed comprehensive ignore patterns |
| Root Android Lint JAR payload (`com/**/*.class`, `META-INF/`, lint data files) | Local, untracked, incompletely ignored | Removed; added standard `*.class` and narrowly named root lint-payload ignores |
| IDE state (`.idea`, `.vscode`, Eclipse metadata, IntelliJ module/project files) | Local/absent, untracked | Added/confirmed ignore patterns |
| `local.properties`, signing stores and build packages | Local/absent, untracked | Added/confirmed repository-wide secret/package ignore patterns |
| Logs, editor backups and OS metadata | Local/absent, untracked | Added standard ignore patterns |
| Two pre-attributes line-ending blobs | Tracked source/script, semantically valid but checkout-dirty on Windows | Renormalized to the existing XML-LF/BAT-CRLF policy; semantic diff is empty |
| `fermata/lib/auto/xposed-api-82.jar` | Tracked intentionally | Required compile-only Auto/Xposed API; retained |
| `gradle/wrapper/gradle-wrapper.jar` | Tracked intentionally | Required Gradle wrapper bootstrap; retained |
| `extracted_logo/` and `product_screenshots/` | Tracked intentionally | Product/documentation assets, not generated build caches; retained |
| `promo/` scripts and source assets | Local, untracked user-authored work | Outside generated-cache scope; preserved untouched. Its own output/work directories remain ignored |
| `carview-static-analysis-*`, `fermatax-dhu-gallery-*`, `exports/`, `dist/`, and ignored local `docs/` material | Local, ignored analysis/evidence/output | Preserved as intentional local evidence; excluded from Git and all source-root scanners |

## Scanner exposure

`ArchitectureBoundaryTest` does not walk the repository root. Hotspot checks read five explicit
tracked source files; core boundary checks walk only `fermata/src/main` and `fermata/src/auto`; addon
checks walk only each `modules/<addon>/src`. Android Lint uses Gradle source sets. Consequently the
ignored build/native trees and the removed root JAR payload were not inputs to either CI gate.

Ad-hoc repository statistics were the exposed path. That gap is closed by removing the contaminating
local payload and documenting tracked-file/source-root enumeration as the required method.

## Verification

An isolated clone of the delivery commit was clean immediately after checkout and contained no
`.gradle/`, `build/`, `.cxx/`, or `local.properties`. From that state:

- `assembleMobileDebug assembleAutoDebug` rebuilt both application variants, all dynamic features,
  and Whisper's native libraries successfully: 1,187 tasks executed in 5m32s.
- Mobile and Auto unit suites, both architecture guards, Mobile and Auto lint, and
  `git diff --check` passed in the same isolated checkout.

Hosted-runner evidence is provided by the GitHub Actions run for the delivery commit.
