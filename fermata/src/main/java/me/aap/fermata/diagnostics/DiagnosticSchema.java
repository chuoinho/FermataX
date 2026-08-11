package me.aap.fermata.diagnostics;

import java.util.Set;

/** Registry for non-user-authored identifiers allowed in exported diagnostic envelopes. */
final class DiagnosticSchema {
	private static final Set<String> CATEGORIES = Set.of(
			"aa_startup", "addon", "application", "audiobook_source", "content",
			"database", "diagnostics", "engine", "engine_exoplayer", "engine_vlc", "legacy_log",
			"hardware_input", "lifecycle", "navigation", "network", "performance", "playback",
			"podcast_source", "process", "radio_source", "service", "service_connection",
			"search", "stremio_protocol", "stremio_subtitle", "stremio_torrent", "tv_source", "voice",
			"web_custom_view", "web_fullscreen", "web_page", "web_playback");

	private static final Set<String> EVENTS = Set.of(
			"aa_activity_destroyed", "aa_startup_started", "activity_created",
			"activity_destroyed", "activity_paused", "activity_resumed",
			"application_initialized", "application_initializing",
			"addon_activity_create", "addon_activity_destroy", "addon_activity_pause",
			"addon_activity_resume", "addon_callback_failed", "addon_install_cancelled",
			"addon_install_completed",
			"addon_install_failed", "addon_install_started", "addon_lifecycle_token_created",
			"addon_lifecycle_token_invalidated", "addon_lifecycle_token_reused",
			"addon_load_cancelled", "addon_load_committed", "addon_load_failed", "addon_load_started",
			"addon_replay_completed", "addon_service_create", "addon_service_destroy",
			"addon_unload_completed", "addon_unload_started", "attach_rejected", "attached",
			"attach_unconfirmed", "audible_start_requested", "browser_callback_received",
			"browser_request_dispatched", "browser_visibility_changed", "command_routed",
			"content_callback_ignored", "content_operation_cancelled",
			"content_operation_completed", "content_operation_failed", "content_operation_started",
			"content_operation_timed_out", "custom_view_rejected", "database_open_retry",
			"database_operation_failed",
			"detach_requested", "detached",
			"diagnostic_clear_failed", "download_cancelled", "download_completed", "download_failed",
			"download_headers_received", "download_started", "ended_signal", "engine_callback_rejected",
			"engine_create_failed", "engine_create_rejected", "engine_create_requested", "engine_created",
			"engine_error", "engine_first_frame", "engine_handoff", "engine_handoff_rejected",
			"engine_handoff_started", "engine_provider_selected", "engine_select_started", "engine_selected",
			"exit_history_collection_failed", "fallback_entered", "first_frame", "logged_exception",
			"logged_failure", "main_frame_error", "main_frame_finished", "main_frame_http_error",
			"main_frame_ssl_error", "main_frame_started", "mute_changed", "navigation_completed",
			"navigation_failed", "navigation_started", "operation_cancelled", "operation_completed",
			"operation_failed", "operation_started", "operation_timed_out", "ownership_adopted",
			"ownership_lost", "paused_signal", "phase_transition", "playback_owner_adopted",
			"playback_owner_commit", "playback_owner_released", "playback_owner_rollback",
			"playback_request_started", "playing_signal", "prepare_callback_rejected", "prepare_rejected",
			"prepare_started", "prepared", "presentation_released", "previous_process_exit",
			"previous_session_without_shutdown_marker", "process_background", "process_foreground",
			"process_started", "ready_signal", "renderer_gone", "report_create_failed",
			"report_save_failed", "report_share_failed", "request_accepted", "request_cancelled",
			"request_completed",
			"request_created", "request_failed", "request_rejected", "request_started",
			"request_timed_out",
			"service_attempt_callback", "service_attempt_started", "service_bind_delegated",
			"service_bind_requested", "service_binding_died", "service_bound", "service_connect_cancelled",
			"service_connect_failed", "service_connect_started", "service_connected",
			"service_connection_failed", "service_create_started", "service_created", "service_destroy_started",
			"service_destroyed", "service_disconnected", "service_failed", "service_null_binding",
			"service_ready", "session_invalidated", "session_marker_write_failed", "session_started",
			"session_state", "signal_rejected", "started", "state_changed", "stopped",
			"suspected_main_thread_stall", "ui_attach_started", "ui_failed", "ui_ready",
			"input_deduplicated", "input_delegated", "input_executed", "input_mapped",
			"input_received", "input_rejected", "input_test_completed", "input_test_started",
			"video_layout_ready", "video_mode_changed", "video_mode_failed");

	private static final Set<String> OPERATIONS = Set.of(
			"media_browser_search", "metadata_database_open", "preparation", "protocol_request",
			"stremio_database_open", "subtitle_discovery", "subtitle_load");

	private DiagnosticSchema() {
	}

	static boolean isCategory(String value) {
		return CATEGORIES.contains(value);
	}

	static boolean isEvent(String value) {
		return EVENTS.contains(value);
	}

	static boolean isOperation(String value) {
		return OPERATIONS.contains(value);
	}
}
