package me.aap.fermata.ui.smarttop;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Objects;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.ui.policy.PlaybackTimelinePolicy;

/** Binds immutable SmartTop semantics into the adaptive layout already resolved by the controller. */
public final class SmartTopBinder {
	public interface Handler {
		void onCard(SmartTopViewState state);

		void onAction(SmartTopAction action, long generation, PlayableItem item);

		void onAllRecent();

		void onQuickRecent(PlayableItem item);
	}

	public record Views(
			View root,
			ImageView sourceIcon,
			TextView eyebrow,
			TextView title,
			TextView subtitle,
			View actions,
			MaterialButton labeledAction,
			List<ImageButton> actionButtons,
			View progressGroup,
			ProgressBar progress,
			TextView progressCurrent,
			TextView progressTotal,
			View recentPanel,
			TextView recentTitle,
			List<TextView> recentItems) {
		public Views {
			Objects.requireNonNull(root, "root");
			Objects.requireNonNull(sourceIcon, "sourceIcon");
			Objects.requireNonNull(eyebrow, "eyebrow");
			Objects.requireNonNull(title, "title");
			Objects.requireNonNull(subtitle, "subtitle");
			Objects.requireNonNull(actions, "actions");
			Objects.requireNonNull(labeledAction, "labeledAction");
			actionButtons = List.copyOf(actionButtons);
			Objects.requireNonNull(progressGroup, "progressGroup");
			Objects.requireNonNull(progress, "progress");
			Objects.requireNonNull(progressCurrent, "progressCurrent");
			Objects.requireNonNull(progressTotal, "progressTotal");
			Objects.requireNonNull(recentPanel, "recentPanel");
			Objects.requireNonNull(recentTitle, "recentTitle");
			recentItems = List.copyOf(recentItems);
		}
	}

	private final Context context;
	private final Handler handler;
	private final boolean backgroundEnabled;

	public SmartTopBinder(Context context, Handler handler) {
		this(context, handler, true);
	}

	public SmartTopBinder(Context context, Handler handler, boolean backgroundEnabled) {
		this.context = Objects.requireNonNull(context, "context");
		this.handler = Objects.requireNonNull(handler, "handler");
		this.backgroundEnabled = backgroundEnabled;
	}

	public void bind(Views views, SmartTopViewState state, boolean editMode) {
		views.root().setTag(R.id.dashboard_smart_state_tag, state);
		views.root().setTag(R.id.dashboard_smart_bind_token,
				new BindToken(state.generation(), itemId(state.presentedItem())));
		bindSourceIcon(views, state);
		bindBackground(views, state);
		views.eyebrow().setVisibility(View.VISIBLE);
		views.eyebrow().setText(state.eyebrow());
		views.title().setText(state.title());
		views.subtitle().setText(state.subtitle());
		views.subtitle().setVisibility(shouldShowSubtitle(state.eyebrow(), state.subtitle()) ?
				View.VISIBLE : View.INVISIBLE);
		views.root().setOnClickListener(editMode ? null : ignored -> {
			SmartTopViewState current = boundState(views.root());
			if (current != null) handler.onCard(current);
		});

		bindActions(views, state, editMode);
		bindTimeline(views, state.timeline());
		bindRecent(views, state, editMode);
	}

	/** Updates only timeline-backed presentation while keeping geometry, artwork and Recent stable. */
	public void bindTimelineUpdate(Views views, SmartTopViewState state) {
		views.root().setTag(R.id.dashboard_smart_state_tag, state);
		bindTimeline(views, state.timeline());
		refreshActionPresentation(views, state);
	}

	private void bindActions(Views views, SmartTopViewState state, boolean editMode) {
		List<ImageButton> buttons = views.actionButtons();
		clearLabeledAction(views.labeledAction());
		for (ImageButton button : buttons) clearAction(button);
		SmartTopLayoutSpec spec = SmartTopLayoutController.layoutSpec(views.root(), state);
		List<SmartTopAction> actions = editMode ? List.of() : spec.visibleActions();
		for (SmartTopAction action : actions) {
			if (isLabeled(action)) {
				bindLabeledAction(views.labeledAction(), views.root(), action, state, spec);
				continue;
			}
			ImageButton button = actionSlot(buttons, action);
			if (button != null) bindAction(button, views.root(), action, state);
		}
		views.actions().setVisibility(actions.isEmpty() ? View.INVISIBLE : View.VISIBLE);
	}

	private static ImageButton actionSlot(List<ImageButton> buttons, SmartTopAction action) {
		if (buttons.size() < 2) return null;
		return switch (action) {
			case PLAY, PLAY_PAUSE -> buttons.get(0);
			case FAVORITE -> buttons.get(1);
			default -> null;
		};
	}

	private void bindAction(ImageButton button, View root, SmartTopAction action,
			SmartTopViewState state) {
		button.setVisibility(View.VISIBLE);
		button.setImageResource(icon(action, state));
		button.setContentDescription(description(action, state));
		button.setActivated(isPrimary(action));
		button.setTag(R.id.dashboard_smart_action_tag, action);
		button.setOnClickListener(ignored -> dispatchAction(button, root));
	}

	private void bindLabeledAction(MaterialButton button, View root, SmartTopAction action,
			SmartTopViewState state, SmartTopLayoutSpec spec) {
		button.setVisibility(View.VISIBLE);
		button.setText(description(action, state));
		if (spec.terminalActionStyle() == SmartTopTerminalActionStyle.LABEL_ONLY) {
			button.setIcon(null);
		} else {
			button.setIconResource(icon(action, state));
		}
		button.setContentDescription(description(action, state));
		button.setActivated(isPrimary(action));
		button.setTag(R.id.dashboard_smart_action_tag, action);
		button.setOnClickListener(ignored -> dispatchAction(button, root));
	}

	private void refreshActionPresentation(Views views, SmartTopViewState state) {
		SmartTopLayoutSpec spec = SmartTopLayoutController.layoutSpec(views.root(), state);
		refreshActionPresentation(views.labeledAction(), state, spec);
		for (ImageButton button : views.actionButtons()) {
			refreshActionPresentation(button, state, spec);
		}
	}

	private void refreshActionPresentation(View button, SmartTopViewState state,
			SmartTopLayoutSpec spec) {
		Object tag = button.getTag(R.id.dashboard_smart_action_tag);
		if (!(tag instanceof SmartTopAction action) || (button.getVisibility() != View.VISIBLE)) return;
		if (button instanceof MaterialButton labeled) {
			if (spec.terminalActionStyle() == SmartTopTerminalActionStyle.LABEL_ONLY) {
				labeled.setIcon(null);
			} else {
				labeled.setIconResource(icon(action, state));
			}
			labeled.setContentDescription(description(action, state));
		} else if (button instanceof ImageButton image) {
			image.setImageResource(icon(action, state));
			image.setContentDescription(description(action, state));
		}
	}

	private void dispatchAction(View button, View root) {
		Object tag = button.getTag(R.id.dashboard_smart_action_tag);
		SmartTopViewState state = boundState(root);
		if ((tag instanceof SmartTopAction action) && (state != null)) {
			handler.onAction(action, state.generation(), state.presentedItem());
		}
	}

	private void bindSourceIcon(Views views, SmartTopViewState state) {
		ImageView sourceIcon = views.sourceIcon();
		ColorStateList tint = context.getColorStateList(R.color.dashboard_smart_action_v2_tint);
		sourceIcon.setImageTintList(tint);
		sourceIcon.setImageResource(state.icon());
	}

	private void bindBackground(Views views, SmartTopViewState state) {
		View root = views.root();
		if (!backgroundEnabled) {
			root.setTag(R.id.dashboard_smart_background_bind_token, null);
			root.setTag(R.id.dashboard_smart_background_drawable_tag, null);
			root.setBackgroundResource(R.drawable.dashboard_smart_top_bg);
			return;
		}

		SmartTopBackground background = state.background();
		SmartTopCardBackgroundFactory.RenderedBackground rendered =
				SmartTopCardBackgroundFactory.create(context, background.kind());
		BackgroundBindToken token = new BackgroundBindToken(state.generation(),
				itemId(state.presentedItem()), background.identity());
		root.setTag(R.id.dashboard_smart_background_bind_token, token);
		root.setTag(R.id.dashboard_smart_background_drawable_tag, rendered.content());
		root.setBackground(rendered.ripple());

		PlayableItem item = state.presentedItem();
		if ((background.kind() != SmartTopBackground.Kind.ARTWORK) ||
				(background.artworkUri() == null) || (item == null) ||
				!SmartTopArtworkResolver.isAllowed(context, background.artworkUri())) return;

		item.getLib().getBitmapCache()
				.getBitmapIfCached(context, background.artworkUri().toString(), false)
				.main().onSuccess(bitmap -> {
					if ((bitmap == null) || bitmap.isRecycled() ||
							!token.equals(root.getTag(
									R.id.dashboard_smart_background_bind_token))) return;
					if (!SmartTopBackgroundPolicy.eligibleDimensions(
							bitmap.getWidth(), bitmap.getHeight(), false)) return;
					Object current = root.getTag(R.id.dashboard_smart_background_drawable_tag);
					if (current == rendered.content()) rendered.content().setArtwork(bitmap);
				});
	}

	private void bindTimeline(Views views, SmartTopTimeline timeline) {
		PlaybackTimelinePolicy.Mode mode = timeline.mode();
		if (mode == PlaybackTimelinePolicy.Mode.HIDDEN) {
			views.progressGroup().setVisibility(View.INVISIBLE);
			views.progress().setProgress(0);
			views.progressCurrent().setText("");
			views.progressTotal().setText("");
			return;
		}

		views.progressGroup().setVisibility(View.VISIBLE);
		if (mode == PlaybackTimelinePolicy.Mode.LIVE) {
			views.progress().setVisibility(View.INVISIBLE);
			views.progress().setProgress(0);
			views.progressCurrent().setVisibility(View.VISIBLE);
			views.progressCurrent().setText(R.string.playback_live);
			views.progressTotal().setVisibility(View.INVISIBLE);
			views.progressTotal().setText("");
			return;
		}

		views.progress().setVisibility(View.VISIBLE);
		views.progress().setProgress(progress(timeline.positionMillis(), timeline.durationMillis()));
		views.progressCurrent().setVisibility(View.VISIBLE);
		views.progressCurrent().setText("");
		views.progressTotal().setVisibility(View.VISIBLE);
		views.progressTotal().setText(formatRemainingTime(context,
				timeline.positionMillis(), timeline.durationMillis()));
	}

	private void bindRecent(Views views, SmartTopViewState state, boolean editMode) {
		SmartTopLayoutSpec spec = SmartTopLayoutController.layoutSpec(views.root(), state);
		int count = Math.min(spec.recentRows(),
				Math.min(state.quickRecent().size(), views.recentItems().size()));
		boolean showPanel = !editMode && (count > 0);
		views.recentPanel().setVisibility(showPanel ? View.VISIBLE : View.GONE);
		views.recentPanel().setOnClickListener(showPanel ? ignored -> handler.onAllRecent() : null);
		views.recentPanel().setClickable(showPanel);
		views.recentPanel().setFocusable(showPanel);

		for (TextView view : views.recentItems()) clearRecent(view);
		if (!showPanel) return;

		for (int i = 0; i < count; i++) {
			PlayableItem item = state.quickRecent().get(i);
			TextView view = views.recentItems().get(i);
			BindToken token = new BindToken(state.generation(), itemId(item));
			view.setTag(R.id.dashboard_smart_bind_token, token);
			view.setVisibility(View.VISIBLE);
			view.setText(item.getName());
			view.setCompoundDrawablesRelativeWithIntrinsicBounds(item.getIcon(), 0, 0, 0);
			view.setCompoundDrawableTintList(
					context.getColorStateList(R.color.dashboard_smart_recent_icon_tint));
			view.setCompoundDrawablePadding(me.aap.utils.ui.UiUtils.toIntPx(context, 6));
			view.setClickable(true);
			view.setFocusable(true);
			view.setOnClickListener(ignored -> handler.onQuickRecent(item));
			item.getMediaData().main().onSuccess(metadata -> {
				if (token.equals(view.getTag(R.id.dashboard_smart_bind_token))) {
					view.setText(PlaybackSnapshot.resolveDisplayTitle(item, metadata));
				}
			});
		}
	}

	private static void clearAction(ImageButton button) {
		button.setVisibility(View.INVISIBLE);
		button.setOnClickListener(null);
		button.setTag(R.id.dashboard_smart_action_tag, null);
		button.setContentDescription(null);
		button.setActivated(false);
		button.setImageDrawable(null);
	}

	private static void clearLabeledAction(MaterialButton button) {
		button.setVisibility(View.GONE);
		button.setOnClickListener(null);
		button.setTag(R.id.dashboard_smart_action_tag, null);
		button.setContentDescription(null);
		button.setActivated(false);
		button.setText("");
		button.setIcon(null);
	}

	private static void clearRecent(TextView view) {
		view.setVisibility(View.GONE);
		view.setText("");
		view.setOnClickListener(null);
		view.setClickable(false);
		view.setFocusable(false);
		view.setTag(null);
		view.setTag(R.id.dashboard_smart_bind_token, null);
		view.setCompoundDrawableTintList(null);
		view.setCompoundDrawablesRelative(null, null, null, null);
		view.setCompoundDrawablePadding(0);
	}

	private int icon(SmartTopAction action, SmartTopViewState state) {
		return switch (action) {
			case PLAY -> R.drawable.play;
			case PLAY_PAUSE -> state.timeline().playing() ? R.drawable.pause : R.drawable.play;
			case FAVORITE -> state.favorite() ? R.drawable.favorite_filled : R.drawable.favorite;
			case OPEN_ADDONS -> R.drawable.view_grid;
			case RETRY -> R.drawable.refresh;
			default -> throw new IllegalArgumentException("Unsupported SmartTop action: " + action);
		};
	}

	private CharSequence description(SmartTopAction action, SmartTopViewState state) {
		return context.getString(switch (action) {
			case PLAY -> R.string.action_play;
			case PLAY_PAUSE -> state.timeline().playing() ? R.string.action_pause : R.string.action_play;
			case FAVORITE -> state.favorite() ? R.string.favorites_remove : R.string.favorites_add;
			case OPEN_ADDONS -> R.string.settings;
			case RETRY -> R.string.retry;
			default -> throw new IllegalArgumentException("Unsupported SmartTop action: " + action);
		});
	}

	static boolean shouldShowSubtitle(CharSequence eyebrow, CharSequence subtitle) {
		if ((subtitle == null) || (subtitle.length() == 0)) return false;
		if ((eyebrow == null) || (eyebrow.length() == 0)) return true;
		return !eyebrow.toString().trim().equalsIgnoreCase(subtitle.toString().trim());
	}

	private static String itemId(PlayableItem item) {
		return (item == null) ? "" : item.getId();
	}

	private static SmartTopViewState boundState(View root) {
		Object state = root.getTag(R.id.dashboard_smart_state_tag);
		return (state instanceof SmartTopViewState smartTop) ? smartTop : null;
	}

	static int progress(long positionMillis, long durationMillis) {
		if (durationMillis <= 0L) return 0;
		return (int) Math.max(0L, Math.min(1000L,
				Math.round((positionMillis * 1000D) / durationMillis)));
	}

	static RemainingTime remainingTime(long remainingMillis) {
		long seconds = Math.max(0L, remainingMillis / 1000L);
		if (seconds < 60L) return RemainingTime.ALMOST_DONE;
		long minutes = seconds / 60L;
		return new RemainingTime((int) Math.min(Integer.MAX_VALUE, minutes / 60L),
				(int) (minutes % 60L));
	}

	static CharSequence formatRemainingTime(Context context, long positionMillis,
			long durationMillis) {
		RemainingTime time = remainingTime(Math.max(0L, durationMillis - positionMillis));
		if (time == RemainingTime.ALMOST_DONE) {
			return context.getString(R.string.dashboard_smart_almost_done);
		}
		if (time.hours() == 0) {
			return context.getString(R.string.dashboard_smart_remaining_minutes, time.minutes());
		}
		return context.getString(R.string.dashboard_smart_remaining_hours_minutes,
				time.hours(), time.minutes());
	}

	private static boolean isLabeled(SmartTopAction action) {
		return SmartTopAdaptivePolicy.isLabeled(action);
	}

	private static boolean isPrimary(SmartTopAction action) {
		return switch (action) {
			case PLAY, PLAY_PAUSE, OPEN_ADDONS, RETRY -> true;
			default -> false;
		};
	}

	public record BindToken(long generation, String itemId) {
	}

	record BackgroundBindToken(long generation, String itemId, String backgroundIdentity) {
	}

	public record RemainingTime(int hours, int minutes) {
		static final RemainingTime ALMOST_DONE = new RemainingTime(0, 0);
	}
}
