# 08 — Requirement traceability matrix

| ID | Requirement | Owner/component | Verification | Gate |
|---|---|---|---|---|
| R01 | Chỉ dùng Stremio Web | `modules/web`; final dependency graph | architecture/dependency scan | 0D, 6, 8 |
| R02 | Không Core/native/server/jlibtorrent | build graph/APK | SBOM, APK native scan, `rg` | 0A, 3, 8 |
| R03 | Một universal APK | CI packaging | exactly-one artifact test | 5, 7, 8 |
| R04 | Reuse Web shell | Web addon hooks/subclasses | hotspot/architecture tests | 1 |
| R05 | Không sibling feature dependency | Gradle graph | architecture guard | 1, 8 |
| R06 | Account/addons/catalog/search/library/details | Stremio Web | production smoke | 0B, 4, 7 |
| R07 | Direct HTTP(S) phát bằng Fermata | client/parser/playable | unit + E2E MP4/HLS | 2, 7 |
| R08 | Không launch external package | client/parser | malicious intent tests | 2, 7 |
| R09 | Fermata physical playback owner | MediaService/MediaSession | ownership/integration tests | 2, 5 |
| R10 | SmartTop từ player thật | PlaybackSnapshot/timeline | SmartTop regression | 5 |
| R11 | Quick Recent OPEN-only | generic provider + route store | spy proves no resolve/play | 1S, 5 |
| R12 | Không mirror DefaultRecent | route store/provider | MediaLib assertions | 1S |
| R13 | Recent exact page | canonical route grammar | route/open E2E | 1S, 5 |
| R14 | Không provider/stream/autoplay từ Recent | provider open flow | negative interaction tests | 1S, 5 |
| R15 | Voice search không autoplay | fragment route | unit/E2E | 1, 5 |
| R16 | Android Auto/DHU | existing host + Web fragment | DHU matrix | 0B, 4, 5 |
| R17 | Renderer/network recovery | existing Web shell + subtype factory | instrumentation/soak | 1, 4, 7 |
| R18 | Magnet/no-server trung thực | Stremio Web boundary | negative E2E | 3 |
| R19 | External server không đóng gói | Web Settings only | dependency/process scan | 3, 8 |
| R20 | Không log secret/URL/title | diagnostics policy | log/secret tests | 4, 7 |
| R21 | Stremio-specific ≤500 LOC | module code | line-budget guard | 1, 7 |
| R22 | Total code controlled | shared boundaries | hotspot report | 1S, 7 |
| R23 | Không automatic fallback | build/cutover | config/behavior test | 6 |
| R24 | Rollback-capable release | source tag/runbook/artifact | rollback drill | 7, release |
| R25 | Legacy cleanup an toàn | repo/build/data retention | full CI/search/backup audit | 8 |
| R26 | Web Browser/YouTube không regression | `modules/web` | existing + targeted tests | 1, 5, 7 |
| R27 | Không DOM polling/Core JS bridge | architecture/security | source guard/review | 1, 4, 7 |
| R28 | Hosted production origin | fragment constant/policy | instrumentation/source guard | 1, 7 |
| R29 | SSL fail-closed | FermataWebClient | SSL/error tests | 4, 7 |
| R30 | One click → one playback | debounce/dispatch | race/double-click E2E | 2, 7 |

## Definition of Done tổng

Mọi R01–R30 phải có bằng chứng PASS hoặc được ghi rõ là limitation ngoài scope. Không được đổi một limitation thành “hoàn thành” bằng wording.

Các limitation ngoài scope cố định:

- local torrent/transcoding/archive/NZB;
- precise Fermata → Stremio progress sync;
- automatic addon subtitle handoff sang Fermata;
- desktop-native player parity.
