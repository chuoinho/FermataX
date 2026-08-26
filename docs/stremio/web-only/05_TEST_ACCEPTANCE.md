# 05 - Acceptance Matrix

| Requirement | Evidence required | Current state |
|---|---|---|
| One Web-only addon, no legacy feature | Gradle projects/dependency graph | PASS |
| Addon is enabled and opens hosted origin | Physical device UI | PASS |
| Existing authenticated Web session remains | Physical device avatar/session UI | PASS |
| Catalog/search/library/settings are usable | Physical device manual test | PARTIAL (detail return after a trailer needs follow-up) |
| Inline HTTP MP4/HLS playback | Physical device with working server | BLOCKED |
| Fullscreen without reload/position loss | Physical device video fixture | BLOCKED |
| Back order | Physical device video fixture | BLOCKED |
| Background/recovery/switching | Physical device lifecycle matrix | BLOCKED |
| No impact to other addons | Existing unit suite plus manual AA sweep | PARTIAL |

`PASS` requires observed evidence, not an inference from source code. A missing streaming server
is a test-environment limitation, not a reason to add a native playback fallback.

On the current physical-device run, the upstream player back affordance changed the hosted route
to the expected detail URL but the detail surface remained blank for at least 20 seconds. No app
crash, renderer-loss callback, or Android ANR was observed. This is retained as a Phase 1C
investigation item; it must be reproduced and attributed before any lifecycle/back acceptance can
move to PASS.
