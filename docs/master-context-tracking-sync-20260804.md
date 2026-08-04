# MASTER_CONTEXT tracking and synchronization — 2026-08-04

## Tracking decision

Decision: **track `MASTER_CONTEXT.md` as shared canonical documentation**.

The file explicitly identifies itself as primary context for maintainers and coding agents and
contains product invariants, architecture ownership, build/test procedures, and change protocol.
It is not a personal scratchpad. No credential or user data was found. The previous ignore entry
was deliberately added with other local build/context artifacts on 2026-07-17, but it now conflicts
with the file's team-facing purpose and caused README/CI documentation to evolve separately.

Before tracking, developer-specific absolute DHU and backup paths were replaced with environment-
based or neutral placeholders. Local ignored SmartTop/UI mockups are explicitly identified as
non-canonical rather than presented as shared repository files.

## Auditable stale-to-current changes

| Section | Previous content | Current content |
| --- | --- | --- |
| Header | Last updated 2026-08-01; ignored local file | Last updated 2026-08-04; tracked canonical maintainer context |
| Engineering rules | Addon isolation had no enforceable module rule; TLS policy absent | Documents CI-enforced zero cross-addon dependency rule and strict-by-default, source-scoped TLS policy |
| Module inventory | Included nonexistent `felex`/`poi`; omitted audiobook, gdrive, podcast, stremio | Lists all 16 physical dynamic-feature module directories and package-map enforcement |
| Playback architecture | `MediaSessionCallback` described as the engine-handoff owner without the completed cutover | Maps lease controller, lease/state-token ownership, transition, and pure prepared-item decisions; records that callback orchestration remains large |
| Addon queue | Five-minute physical-operation timeout | Actual three-minute timeout, FIFO continuation, exactly-once/late-completion behavior |
| Refactoring status | Called the refactor uncommitted and stopped at the earlier phases | Records committed engine-lease cutover, addon timeout, refresh backoff, lint resolution, scoped TLS, CI, and cross-addon guard |
| Local archives | Embedded one developer's absolute `E:\...` backup paths | Uses neutral `<local-backup-directory>` placeholders while preserving historical hashes |
| Toolchain | Omitted Gradle version | Confirms JDK 17, Gradle 9.5.1, AGP 9.2.1, SDK 36, min SDK 28, NDK 29.0.14206865 |
| Verification commands | Partial compile/module-test examples only | Canonical Mobile/Auto suites, both architecture methods, explicit Mobile/Auto lint, and `git diff --check` |
| Automated status | Frozen 167-test Phase-8 count and release-only claims | CI is the current source status; all five gates are named and historical artifact evidence is scoped to its snapshot |
| DHU setup | Absolute `C:\Users\...` executable path | Resolves DHU through `ANDROID_SDK_ROOT` or `LOCALAPPDATA` |
| Runtime snapshot | Labeled current despite version-code 294 artifact versus source version 301 | Explicitly historical 2026-07 artifact/DHU evidence |
| Git/workspace state | Frozen HEAD `585fe148` and local dirty-state narrative | Requires live `git status`/`git log`; preserves unrelated changes without encoding ephemeral state |
| Technical debt | Engine characterization and subtitle harness listed as unfinished | Marks lease cutover and deterministic subtitle harness complete; retains remaining orchestration, TLS-warning, lint-warning, and UI coupling risks |
| Documentation roles | README and ignored master context could diverge | README is concise onboarding/status; tracked master context is canonical product/architecture documentation |

## Verification

This is a documentation/tracking change only. The standard Mobile, Auto, architecture boundary,
Mobile/Auto lint, and whitespace gates are run locally and on GitHub Actions. Hosted-runner evidence
is supplied in the task delivery report for the implementation commit.
