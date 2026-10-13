# Glass UI Screen + State + Modal Inventory (C01–C16)

Date: 2026-10-13  
Branch: `feat/aa-glass-theme`  
Default Appearance: **NIGHT** (`MainActivityPrefs.GLASS_APPEARANCE_NIGHT = 1`)  
Quick Bar Rule: **4 slots (narrow) / 6 slots (full)** total, counting Addons and Exit.  
Independent Theme Gating: `THEME_MAIN` (Phone) and `THEME_AA` (Automotive) independent; `GLASS_APPEARANCE` shared; zero `uiMode` auto-switching.

---

## Screen + State + Modal Inventory Matrix

| ID | Screen / Surface | Owner Component | Callers / Contexts | States Required | Modals / Dialogs | Test / Verification Criteria |
|---|---|---|---|---|---|---|
| **C01** | Topbar | `FermataToolBarView`, `ToolBarMediator` | Phone & AA roots & sub-screens | Normal, back visible, action icons, title text, subtitle, search mode | Search dropdown | Back arrow navigation from sub-screens, search query clear, title contrast |
| **C02** | Navbar | `FermataNavBarView`, `NavBarMediator` | Phone & AA side/bottom bar | Normal, selected item, focused, disabled, rotary focus | Navigation context menu | Correct active highlight, insets respected, rotary navigation |
| **C03** | Phone Shell | `PhonePlaybackCarHeaderController`, `PhoneSupportBannerController`, `PhoneBottomMenuController` | Phone portrait/landscape main activity | Single header hierarchy, non-duplicated settings/gear, clean bottom nav | Theme picker dialog | No redundant headers; bottom nav matches glass acrylic spec |
| **C04** | Text / Icon / Button | Semantic tokens (`GlassThemeColors`, `GlassDimensions`) | App-wide | Text primary/secondary/tertiary, icon normal/focused, button primary/secondary/destructive/disabled | N/A | High contrast in Day & Night; touch targets >= 48dp (phone), >= 76dp (AA) |
| **C05** | Menu / Popup | `OverlayMenuView`, `OverlayMenuItemView` | Overflow menus, media item action menus | Open, closed, item normal/pressed/focused/selected/disabled | Long-press context menu, sort menu | Semantic text colors in Day and Night; rotary focus highlight |
| **C06** | Dialogs | `GlassDialogBuilder`, `DialogView` | Confirmations, alerts, choices, custom dialogs | Alert, confirm, prompt, single-choice, multi-choice, custom view | Shortcut picker, replace/remove shortcut, Car Finder confirm, EQ popups | Unified glass styling; preserves validation, cancel, and IME |
| **C07** | Settings / Forms | `GlassSettingsDecorator`, `PreferenceView`, `GenericDialogFragment` | Main Settings, Addon settings, Source config dialogs | Section header, switch, slider, list preference, text input, button action | List choice dialog, number picker | Readable Day & Night text; switches & sliders styled |
| **C08** | Media Lists / Grid | `MediaItemView`, `GlassMediaLibDecorator`, `MediaLibFragment` | Folders, Playlists, Favorites, Recent, TV, Radio, Podcasts | Item normal, selected, playing/active (cyan border), loading, empty state, error/retry | Item context menu, playlist add | Preserves glass card background; active track highlighted; empty state action |
| **C09** | File & Source Providers | `FilePickerFragment`, SMB, SFTP, M3U/Xtream forms | Add folder sheet, VFS sources, IPTV sources | Path input, breadcrumb, folder row, file row, permission gate, auth form | New folder dialog, connection test | Input fields styled with glass tokens; IME next/done; cancel keeps state |
| **C10** | Player / EQ | `ControlPanel`, `AudioEffectsScreenView`, `SubtitlesView`, `VideoInfoView` | Fullscreen video player, hero player, equalizer screen | Controls hidden, controls visible, playing, paused, live, buffering, seekbar drag | EQ virtualizer popup, numeric input | 1st tap reveals controls, 2nd tap pauses; EQ 10 bands + switches styled |
| **C11** | AA Keyboard | `CarKeyboardOverlay` | Android Auto search & text input | Normal key, pressed, shift active, space, backspace, enter, disabled | Overlay keyboard modal | Glass acrylic background, high-contrast key labels, Day/Night manual palette |
| **C12** | Onboarding / Voice | `InitialSetupFragment`, `VoiceRecognitionSession`, `ReadinessPrefsBuilder` | App first launch, mic button, connection test | Listening, processing, speech recognized, mic permission denied, error | Voice overlay sheet | Glass pill buttons; high contrast text in listening state; readiness checklist |
| **C13** | Backup / Diagnostics | `SettingsFragment` backup actions, `LogViewer` | Backup/restore preferences, system diagnosis | Exporting, importing, file select, confirmation, success, failure | Confirm restore dialog | Destructive restore warning styled; preserves backup data integrity |
| **C14** | Car Finder | `CarFinderFragment`, `GlassRadarView`, `CarFinderSettingsDialog` | Car parking location tracker | No pinned car, car pinned, compass active, GPS searching, map view | Save location confirm, clear confirm, settings | Cyberpunk radar scan rings; high contrast map overlay controls |
| **C15** | Web Addons Chrome | `YouTube`, `Web`, `TikTok`, `Stremio` chrome views | Web addon modules | Address bar, back/forward/refresh, bookmark, fullscreen toggle, loading bar | Page actions menu, JS alert/confirm | App-owned chrome strictly glass; web content unmolested |
| **C16** | Media Engines & Cast | `ExoPlayer`, `VLCPlayer`, Cast controls | Playback backend integration | Audio only, video rendering, cast connected, cast disconnected | Device picker dialog | App-owned cast & transport buttons follow glass tokens |
