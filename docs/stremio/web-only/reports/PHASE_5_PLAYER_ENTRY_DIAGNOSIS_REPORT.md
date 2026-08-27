# Phase 5A: Stream-to-Player Entry Diagnosis

## Scope and Result

**Result: `BLOCKED_NO_SAFE_EXISTING_DIRECT_STREAM`**

This is a diagnostic checkpoint only. No production or test code was changed, no
fixture was started or installed, and no playback-matrix item was run.

The six restored addons were verified in the FermataX Stremio UI:

1. Cinemeta
2. YouTube
3. WatchHub
4. Public Domain Movies
5. OpenSubtitles v3
6. Local Files (without catalog support)

`Fermata Local Validation` was absent.

## Environment Evidence

- Device: physical ADB device `15c36230` (Redmi Note 8).
- TCP port 7000: closed; no listener was present.
- `adb reverse --list`: empty; there was no `tcp:7000` reverse rule.
- No Phase 5A CDP session, ADB forward, fixture process, or temporary server was
  created.
- `dumpsys media_session` showed Fermata's active `FermataMediaService` session
  in `NONE` state with position and buffered position zero before any attempted
  selection.

Two pre-existing host ADB forwards (`tcp:9223` to a WebView DevTools socket and
`tcp:5277` to `tcp:5277`) were observed after the baseline check. Their owner and
purpose were not established by this phase, so they were deliberately not
removed. They were not created or used by Phase 5A.

## External Player Setting

The Stremio UI Settings page was opened through normal UI navigation.

| Observation | Value |
| --- | --- |
| Before Phase 5A | `Disabled` |
| After Phase 5A | `Disabled` |
| Change made | None; the required internal-player value was already selected. |

No external-player, VLC, or `Allow choosing` option was selected.

## Safe Stream Discovery

The only installed addon named by the checkpoint for public-domain discovery,
`Public Domain Movies`, advertises itself in the installed-addon UI as:
"Torrents for public domain movies." Its available catalog selector was reached
through the normal Discover UI, but it does not establish a direct HTTP/HTTPS
MP4 candidate.

The other restored addons did not supply a safe, existing direct HTTP/HTTPS MP4
candidate within the allowed UI-only scope:

- Cinemeta is metadata/catalogue only.
- WatchHub resolves viewing providers rather than an in-app direct test stream.
- YouTube is not a direct MP4 test source for this checkpoint.
- OpenSubtitles is subtitle-only.
- Local Files is not an existing public-domain network stream source.

Consequently, selecting any available Public Domain Movies source would have
tested a torrent, which Phase 5A expressly excludes. No stream control was
activated.

## Route, Network, Player, and Console Evidence

No safe stream selection occurred, so passive capture was not armed and no
media request, player route transition, HTML5-video load, main-frame external
scheme block, console exception, or logcat event can be attributed to a Phase
5A selection.

The diagnostic classification A/B/C is therefore **not applicable**. In
particular, this phase does not confirm or reject the hypothesis that `Allow
choosing` can route a stream to a blocked external-player scheme: the setting
was already `Disabled`, and there was no permissible direct stream with which to
exercise the boundary.

## Matrix Status

Not run: direct MP4 playback, HLS, torrent, subtitle, seek, fullscreen,
lifecycle, resume, and `nexttrack`. The playback matrix remains stopped at the
Stream-to-Player checkpoint.

## Cleanup and Change Audit

- No fixture, server, reverse rule, or Phase 5A CDP/ADB forward was created.
- No credentials, token, cookie, IndexedDB, localStorage, or authentication
  data was read.
- No external application/player was opened.
- No site data was cleared.
- Production/test LOC: `0`.
- This report is the only Phase 5A change.

The prior Phase 4 report remains an existing uncommitted documentation file and
was not edited by Phase 5A.
