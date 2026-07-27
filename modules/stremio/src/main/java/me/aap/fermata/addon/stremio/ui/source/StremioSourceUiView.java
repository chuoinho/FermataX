package me.aap.fermata.addon.stremio.ui.source;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import me.aap.fermata.addon.stremio.R;
import me.aap.fermata.addon.stremio.ui.config.StremioConfigDialog;
import me.aap.fermata.addon.stremio.ui.config.StremioConfigLaunch;
import me.aap.fermata.addon.stremio.ui.source.SourceUiController.EditorRequest;
import me.aap.fermata.addon.stremio.ui.source.SourceUiController.SourceUiState;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;

/** Reusable mobile/AA source-management panel. Runtime wiring is supplied through the gateway. */
public final class StremioSourceUiView extends FrameLayout implements SourceUiController.Listener {
	private static final long UI_OPERATION_TIMEOUT_MILLIS = 30_000L;
	private static final int ACTION_EDIT = 0x53540001;
	private static final int ACTION_TOGGLE = 0x53540002;
	private static final int ACTION_REFRESH = 0x53540003;
	private static final int ACTION_REMOVE = 0x53540004;
	private static final int ACTION_CONFIGURE = 0x53540005;
	private static final int ACTION_DISCOVERY_BASE = 0x53541000;

	private final RecyclerView list;
	private final TextView empty;
	private final View initialLoading;
	private final View operation;
	private final View cancel;
	private final SourceAdapter adapter;
	private final ItemTouchHelper touchHelper;
	private SourceUiGateway gateway;
	private MainActivityDelegate activity;
	private SourceUiController controller;
	private CompletableFuture<StremioConfigLaunch> configLoad;
	private CompletableFuture<List<SourceUiDiscoveryItem>> discoveryLoad;
	private SourceUiOperation discoveryInstall;
	private Runnable configDeadline;
	private Runnable discoveryDeadline;
	private Runnable installDeadline;
	private BiConsumer<SourceUiResult, Throwable> editorCompletion;

	public StremioSourceUiView(@NonNull Context context) {
		this(context, null);
	}

	public StremioSourceUiView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		LayoutInflater.from(context).inflate(R.layout.stremio_source_list, this, true);
		list = findViewById(R.id.stremio_source_list);
		empty = findViewById(R.id.stremio_sources_empty);
		initialLoading = findViewById(R.id.stremio_source_initial_loading);
		operation = findViewById(R.id.stremio_source_operation);
		cancel = findViewById(R.id.stremio_source_cancel);
		adapter = new SourceAdapter();
		list.setLayoutManager(new LinearLayoutManager(context));
		list.setAdapter(adapter);
		touchHelper = new ItemTouchHelper(new DragCallback());
		touchHelper.attachToRecyclerView(list);
		findViewById(R.id.stremio_source_add).setOnClickListener(view -> {
			if (controller != null) controller.requestAdd();
		});
		findViewById(R.id.stremio_source_discover).setOnClickListener(view -> discoverAddons());
		cancel.setOnClickListener(view -> {
			if (controller != null) controller.cancelPending();
		});
	}

	/** Binds but does not own the gateway or activity. Safe to call before or after attachment. */
	public void bind(SourceUiGateway gateway, MainActivityDelegate activity) {
		bind(gateway, activity, (result, error) -> {
		});
	}

	/** Keeps editor mutations independent from this panel's attach/detach lifecycle. */
	public void bind(SourceUiGateway gateway, MainActivityDelegate activity,
			BiConsumer<SourceUiResult, Throwable> editorCompletion) {
		this.gateway = Objects.requireNonNull(gateway, "gateway");
		this.activity = Objects.requireNonNull(activity, "activity");
		this.editorCompletion = Objects.requireNonNull(editorCompletion, "editorCompletion");
		restartController();
	}

	public void unbind() {
		cancelConfigLoad();
		cancelDiscovery();
		closeController();
		gateway = null;
		activity = null;
		editorCompletion = null;
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		restartController();
	}

	@Override
	protected void onDetachedFromWindow() {
		cancelConfigLoad();
		cancelDiscovery();
		closeController();
		super.onDetachedFromWindow();
	}

	@Override
	public void render(SourceUiState state) {
		boolean loading = state.initialLoading();
		boolean busy = state.pending() != null;
		initialLoading.setVisibility(loading ? VISIBLE : GONE);
		operation.setVisibility(busy ? VISIBLE : GONE);
		cancel.setVisibility(busy && state.pending().cancellable() ? VISIBLE : GONE);
		empty.setVisibility(!loading && state.snapshot().sources().isEmpty() ? VISIBLE : GONE);
		adapter.replace(state.snapshot().sources(), busy);
	}

	@Override
	public void showEditor(EditorRequest request) {
		MainActivityDelegate host = activity;
		SourceUiGateway currentGateway = gateway;
		BiConsumer<SourceUiResult, Throwable> completion = editorCompletion;
		if ((host == null) || (currentGateway == null) || (completion == null)) return;
		StremioSourceEditorDialog.show(host, request, draft -> {
			final SourceUiOperation operation;
			try {
				operation = request.isEdit() ?
						currentGateway.edit(request.sourceUuid(), draft) :
						currentGateway.add(draft);
			} catch (RuntimeException error) {
				host.post(() -> completion.accept(null, error));
				return;
			}
			operation.completion().whenComplete((result, error) ->
					host.post(() -> completion.accept(result, error)));
		});
	}

	@Override
	public void confirmRemove(SourceUiItem source) {
		MainActivityDelegate host = activity;
		SourceUiController current = controller;
		if ((host == null) || (current == null)) return;
		host.createDialogBuilder(getContext())
				.setTitle(R.string.stremio_source_remove_title)
				.setMessage(getContext().getString(R.string.stremio_source_remove_message,
						source.name()))
				.setPositiveButton(R.string.stremio_source_remove,
						(dialog, which) -> current.removeConfirmed(source.sourceUuid()))
				.setNegativeButton(R.string.stremio_source_cancel, null)
				.show();
	}

	@Override
	public void showError(SourceUiError error) {
		if ((error == SourceUiError.NONE) || (error == SourceUiError.CANCELLED)) return;
		UiUtils.showAlert(getContext(), errorText(getContext(), error).toString());
	}

	public static CharSequence errorText(Context context, SourceUiError error) {
		return context.getText(switch (error) {
			case CANCELLED -> R.string.stremio_source_error_cancelled;
			case CLOSED -> R.string.stremio_source_error_closed;
			case CONCURRENT_MODIFICATION -> R.string.stremio_source_error_concurrent;
			case DUPLICATE_SOURCE -> R.string.stremio_source_error_duplicate;
			case INVALID_MANIFEST -> R.string.stremio_source_error_manifest;
			case INVALID_ORDER -> R.string.stremio_source_error_order;
			case INVALID_URL -> R.string.stremio_source_error_url;
			case NOT_FOUND -> R.string.stremio_source_error_not_found;
			case PERSISTENCE -> R.string.stremio_source_error_persistence;
			case ROLLBACK -> R.string.stremio_source_error_rollback;
			case SECRET_REJECTED -> R.string.stremio_source_error_secret;
			case SECURE_STORAGE -> R.string.stremio_source_error_secure_storage;
			case TRANSPORT -> R.string.stremio_source_error_transport;
			case CLEARTEXT_CONSENT_REQUIRED -> R.string.stremio_source_error_http_consent;
			case LAN_CONSENT_REQUIRED -> R.string.stremio_source_error_lan_consent;
			case NONE, UNKNOWN -> R.string.stremio_source_error_unknown;
		});
	}

	private void restartController() {
		if (!isAttachedToWindow() || (gateway == null) || (activity == null) ||
				(controller != null)) return;
		controller = new SourceUiController(gateway, this, command -> post(command));
		controller.start();
	}

	private void closeController() {
		SourceUiController current = controller;
		controller = null;
		if (current != null) current.close();
	}

	private void showSourceMenu(SourceUiItem source) {
		MainActivityDelegate host = activity;
		if (host == null) return;
		host.getContextMenu().show(builder -> {
			builder.setTitle(source.name());
			OverlayMenu.Builder menu = builder.withSelectionHandler(item ->
					onMenuItem(source, item));
			menu.addItem(ACTION_EDIT, me.aap.fermata.R.drawable.edit,
					R.string.stremio_source_edit);
			menu.addItem(ACTION_TOGGLE, me.aap.utils.R.drawable.check,
					source.enabled() ? R.string.stremio_source_disable :
							R.string.stremio_source_enable);
			menu.addItem(ACTION_REFRESH, me.aap.fermata.R.drawable.refresh,
					R.string.stremio_source_refresh);
			if (source.configurable()) {
				menu.addItem(ACTION_CONFIGURE, me.aap.fermata.R.drawable.settings,
						R.string.stremio_source_configure);
			}
			menu.addItem(ACTION_REMOVE, me.aap.fermata.R.drawable.delete,
					R.string.stremio_source_remove);
		});
	}

	private boolean onMenuItem(SourceUiItem source, OverlayMenuItem item) {
		SourceUiController current = controller;
		if (current == null) return true;
		switch (item.getItemId()) {
			case ACTION_EDIT -> current.requestEdit(source.sourceUuid());
			case ACTION_TOGGLE -> current.setEnabled(source.sourceUuid(), !source.enabled());
			case ACTION_REFRESH -> current.refresh(source.sourceUuid());
			case ACTION_CONFIGURE -> openConfiguration(source);
			case ACTION_REMOVE -> current.requestRemove(source.sourceUuid());
			default -> {
			}
		}
		return true;
	}

	private void openConfiguration(SourceUiItem source) {
		SourceUiGateway currentGateway = gateway;
		MainActivityDelegate host = activity;
		if ((currentGateway == null) || (host == null) || (configLoad != null)) return;
		final CompletableFuture<StremioConfigLaunch> loading;
		try {
			loading = Objects.requireNonNull(
					currentGateway.loadConfiguration(source.sourceUuid()), "config load");
		} catch (RuntimeException error) {
			showError(SourceUiError.UNKNOWN);
			return;
		}
		configLoad = loading;
		configDeadline = () -> {
			if (configLoad != loading) return;
			configLoad = null;
			configDeadline = null;
			loading.cancel(true);
			showError(SourceUiError.TRANSPORT);
		};
		postDelayed(configDeadline, UI_OPERATION_TIMEOUT_MILLIS);
		loading.whenComplete((launch, error) -> post(() -> {
			if (configLoad != loading) return;
			configLoad = null;
			cancelConfigDeadline();
			if ((error != null) || (launch == null)) {
				showError(SourceUiError.UNKNOWN);
				return;
			}
			StremioConfigDialog.show(host, source.name(), source.sourceUuid(), currentGateway,
					launch, () -> {
					});
		}));
	}

	private void cancelConfigLoad() {
		CompletableFuture<StremioConfigLaunch> loading = configLoad;
		configLoad = null;
		cancelConfigDeadline();
		if (loading != null) loading.cancel(true);
	}

	private void cancelConfigDeadline() {
		Runnable deadline = configDeadline;
		configDeadline = null;
		if (deadline != null) removeCallbacks(deadline);
	}

	private void discoverAddons() {
		SourceUiGateway currentGateway = gateway;
		MainActivityDelegate host = activity;
		if ((currentGateway == null) || (host == null) || (discoveryLoad != null) ||
				(discoveryInstall != null)) return;
		initialLoading.setContentDescription(getContext().getString(
				R.string.stremio_discover_loading));
		initialLoading.setVisibility(VISIBLE);
		final CompletableFuture<List<SourceUiDiscoveryItem>> loading;
		try {
			loading = Objects.requireNonNull(currentGateway.discover(), "discovery load");
		} catch (RuntimeException error) {
			initialLoading.setVisibility(GONE);
			showError(SourceUiError.TRANSPORT);
			return;
		}
		discoveryLoad = loading;
		discoveryDeadline = () -> {
			if (discoveryLoad != loading) return;
			discoveryLoad = null;
			discoveryDeadline = null;
			loading.cancel(true);
			initialLoading.setVisibility(GONE);
			showError(SourceUiError.TRANSPORT);
		};
		postDelayed(discoveryDeadline, UI_OPERATION_TIMEOUT_MILLIS);
		loading.whenComplete((items, error) -> post(() -> {
			if (discoveryLoad != loading) return;
			discoveryLoad = null;
			cancelDiscoveryDeadline();
			initialLoading.setVisibility(GONE);
			if (error != null) {
				showError(SourceUiError.TRANSPORT);
				return;
			}
			if ((items == null) || items.isEmpty()) {
				UiUtils.showAlert(getContext(), R.string.stremio_discover_empty);
				return;
			}
			showDiscoveredItems(List.copyOf(items));
		}));
	}

	private void showDiscoveredItems(List<SourceUiDiscoveryItem> items) {
		MainActivityDelegate host = activity;
		if (host == null) return;
		host.getContextMenu().show(builder -> {
			builder.setTitle(R.string.stremio_discover_addons);
			for (int i = 0; i < items.size(); i++) {
				SourceUiDiscoveryItem item = items.get(i);
				String state = getContext().getString(item.installed() ?
						R.string.stremio_discover_installed : item.official() ?
						R.string.stremio_discover_official : R.string.stremio_discover_community);
				builder.addItem(ACTION_DISCOVERY_BASE + i, me.aap.fermata.R.drawable.video,
						item.name() + " | " + state).setData(item).setMultiLine(true)
						.setHandler(selected -> {
							showDiscoveryDetails(selected.getData());
							return true;
						});
			}
		});
	}

	private void showDiscoveryDetails(SourceUiDiscoveryItem item) {
		MainActivityDelegate host = activity;
		if (host == null) return;
		StringBuilder details = new StringBuilder();
		if (!item.version().isEmpty()) details.append('v').append(item.version());
		if (!item.description().isEmpty()) {
			if (!details.isEmpty()) details.append("\n\n");
			details.append(item.description());
		}
		if (item.protectedAddon() || item.configurable()) {
			if (!details.isEmpty()) details.append("\n\n");
			details.append(getContext().getString(R.string.stremio_discover_protected));
		}
		var builder = host.createDialogBuilder(getContext())
				.setTitle(item.name())
				.setMessage(details)
				.setNegativeButton(R.string.stremio_source_cancel, null);
		if (!item.installed()) builder.setPositiveButton(R.string.stremio_discover_install,
				(dialog, which) -> installDiscovered(item));
		builder.show();
	}

	private void installDiscovered(SourceUiDiscoveryItem item) {
		SourceUiGateway currentGateway = gateway;
		MainActivityDelegate host = activity;
		if ((currentGateway == null) || (host == null) || (discoveryInstall != null)) return;
		final SourceUiOperation install;
		try {
			install = Objects.requireNonNull(currentGateway.installDiscovered(item.stableId()),
					"discovery install");
		} catch (RuntimeException error) {
			showError(SourceUiError.UNKNOWN);
			return;
		}
		discoveryInstall = install;
		operation.setVisibility(VISIBLE);
		installDeadline = () -> {
			if (discoveryInstall != install) return;
			discoveryInstall = null;
			installDeadline = null;
			install.cancel();
			operation.setVisibility(GONE);
			showError(SourceUiError.TRANSPORT);
		};
		postDelayed(installDeadline, UI_OPERATION_TIMEOUT_MILLIS);
		install.completion().whenComplete((result, error) -> post(() -> {
			if (discoveryInstall != install) return;
			discoveryInstall = null;
			cancelInstallDeadline();
			operation.setVisibility(GONE);
			if (error != null) showError(SourceUiError.UNKNOWN);
			else if ((result != null) && (result.status() == SourceUiResult.Status.FAILED)) {
				showError(result.error());
			}
		}));
	}

	private void cancelDiscovery() {
		CompletableFuture<List<SourceUiDiscoveryItem>> loading = discoveryLoad;
		discoveryLoad = null;
		cancelDiscoveryDeadline();
		if (loading != null) loading.cancel(true);
		SourceUiOperation install = discoveryInstall;
		discoveryInstall = null;
		cancelInstallDeadline();
		if (install != null) install.cancel();
	}

	private void cancelDiscoveryDeadline() {
		Runnable deadline = discoveryDeadline;
		discoveryDeadline = null;
		if (deadline != null) removeCallbacks(deadline);
	}

	private void cancelInstallDeadline() {
		Runnable deadline = installDeadline;
		installDeadline = null;
		if (deadline != null) removeCallbacks(deadline);
	}

	private final class SourceAdapter extends RecyclerView.Adapter<SourceHolder> {
		private final List<SourceUiItem> sources = new ArrayList<>();
		private boolean busy;
		private boolean moved;

		@Override
		@NonNull
		public SourceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.stremio_source_row, parent, false);
			return new SourceHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull SourceHolder holder, int position) {
			holder.bind(sources.get(position), busy);
		}

		@Override
		public int getItemCount() {
			return sources.size();
		}

		void replace(List<SourceUiItem> replacement, boolean busy) {
			this.busy = busy;
			if (!moved || !sameIds(sources, replacement)) {
				sources.clear();
				sources.addAll(replacement);
				moved = false;
			}
			notifyDataSetChanged();
		}

		boolean move(int from, int to) {
			if (busy || (from < 0) || (to < 0) || (from >= sources.size()) ||
					(to >= sources.size())) return false;
			Collections.swap(sources, from, to);
			moved = true;
			notifyItemMoved(from, to);
			return true;
		}

		void commitOrder() {
			if (!moved) return;
			moved = false;
			SourceUiController current = controller;
			if (current != null) current.reorder(sources.stream()
					.map(SourceUiItem::sourceUuid).toList());
		}
	}

	private final class SourceHolder extends RecyclerView.ViewHolder {
		private final TextView name;
		private final TextView summary;
		private final TextView capabilities;
		private final CheckBox enabled;
		private final ImageView more;
		private final ImageView drag;

		SourceHolder(View itemView) {
			super(itemView);
			name = itemView.findViewById(R.id.stremio_source_name);
			summary = itemView.findViewById(R.id.stremio_source_summary);
			capabilities = itemView.findViewById(R.id.stremio_source_capabilities);
			enabled = itemView.findViewById(R.id.stremio_source_enabled);
			more = itemView.findViewById(R.id.stremio_source_more);
			drag = itemView.findViewById(R.id.stremio_source_drag_handle);
		}

		void bind(SourceUiItem source, boolean busy) {
			name.setText(source.name());
			String state = getContext().getString(source.enabled() ?
					R.string.stremio_source_enabled : R.string.stremio_source_disabled);
			String error = (source.lastErrorCode() == null) ? "" :
					" | " + getContext().getString(R.string.stremio_source_last_error);
			summary.setText(source.version() + " | " + state + error);
			capabilities.setText(capabilityText(source));
			enabled.setOnCheckedChangeListener(null);
			enabled.setChecked(source.enabled());
			enabled.setEnabled(!busy);
			enabled.setContentDescription(getContext().getString(source.enabled() ?
					R.string.stremio_source_disable : R.string.stremio_source_enable));
			enabled.setOnCheckedChangeListener((button, checked) -> {
				SourceUiController current = controller;
				if (current != null) current.setEnabled(source.sourceUuid(), checked);
			});
			itemView.setEnabled(!busy);
			itemView.setOnClickListener(view -> {
				SourceUiController current = controller;
				if (current != null) current.requestEdit(source.sourceUuid());
			});
			itemView.setOnLongClickListener(view -> {
				showSourceMenu(source);
				return true;
			});
			more.setEnabled(!busy);
			more.setOnClickListener(view -> showSourceMenu(source));
			drag.setEnabled(!busy);
			drag.setOnTouchListener((view, event) -> {
				if (!busy && (event.getActionMasked() == MotionEvent.ACTION_DOWN)) {
					touchHelper.startDrag(this);
					return true;
				}
				return false;
			});
		}

		private CharSequence capabilityText(SourceUiItem source) {
			StringJoiner labels = new StringJoiner(" | ");
			for (SourceUiCapability capability : SourceUiCapability.values()) {
				if (!source.capabilities().contains(capability)) continue;
				labels.add(getContext().getString(switch (capability) {
					case CATALOG -> R.string.stremio_source_capability_catalog;
					case META -> R.string.stremio_source_capability_meta;
					case STREAM -> R.string.stremio_source_capability_stream;
					case SUBTITLE -> R.string.stremio_source_capability_subtitle;
					case ADDON_CATALOG -> R.string.stremio_source_capability_addon_catalog;
				}));
			}
			return labels.length() == 0 ?
					getContext().getText(R.string.stremio_source_capability_none) : labels.toString();
		}
	}

	private final class DragCallback extends ItemTouchHelper.SimpleCallback {
		DragCallback() {
			super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
		}

		@Override
		public boolean isLongPressDragEnabled() {
			return false;
		}

		@Override
		public boolean onMove(@NonNull RecyclerView recyclerView,
				@NonNull RecyclerView.ViewHolder source,
				@NonNull RecyclerView.ViewHolder target) {
			return adapter.move(source.getBindingAdapterPosition(),
					target.getBindingAdapterPosition());
		}

		@Override
		public void clearView(@NonNull RecyclerView recyclerView,
				@NonNull RecyclerView.ViewHolder viewHolder) {
			super.clearView(recyclerView, viewHolder);
			adapter.commitOrder();
		}

		@Override
		public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
		}
	}

	private static boolean sameIds(List<SourceUiItem> first, List<SourceUiItem> second) {
		if (first.size() != second.size()) return false;
		for (int i = 0; i < first.size(); i++) {
			if (!first.get(i).sourceUuid().equals(second.get(i).sourceUuid())) return false;
		}
		return true;
	}
}
