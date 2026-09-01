# 06 - Rollout and Rollback

`StremioWebAddon` is the sole Stremio addon and retains the stable `stremio_fragment` identity.
Rollback is a normal source revert of the hosted implementation. WebView cookies and storage remain
Android-owned and are never migrated, rewritten, or backed up by FermataX.
