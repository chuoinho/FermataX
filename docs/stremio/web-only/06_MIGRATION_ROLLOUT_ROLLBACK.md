# 06 - Migration, Rollout and Rollback

The migration is build-time selected. Test builds use `-PWEB_STREMIO=true`; this removes legacy
`:stremio` and registers `StremioWebAddon` under the same logical addon ID. Default builds remain
on the current graph until a later cutover decision.

Rollback is a rebuild without `WEB_STREMIO=true` or a revert of the focused Phase 1 commits.
WebView cookies/storage remain Android-owned and are not migrated, rewritten or backed up by this
change. Do not attempt to convert native Stremio history or playback state into Web state.
