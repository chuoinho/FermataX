package me.aap.fermata.ui.smarttop;

import android.graphics.Paint;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;

import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.ui.view.MinimumTouchTargetDelegate;
import me.aap.utils.ui.UiUtils;

/** Applies a complete adaptive SmartTop layout spec without host-specific space rules. */
public final class SmartTopLayoutController {
	private SmartTopLayoutController() {
	}

	public static void apply(View root, SmartTopViewState state, boolean automotive) {
		SmartTopEnvironment environment = environment(root, automotive);
		SmartTopContentMetrics metrics = contentMetrics(root, state, environment);
		SmartTopLayoutSpec spec = SmartTopAdaptivePolicy.resolve(environment, state.actions(), metrics);
		LayoutToken token = new LayoutToken(environment, spec);
		if (token.equals(root.getTag(R.id.dashboard_smart_layout_token))) return;

		int padding = px(root, spec.cardPaddingDp());
		root.setPadding(padding, padding, padding, padding);
		int height = px(root, spec.cardHeightDp());
		ViewGroup.LayoutParams rootParams = root.getLayoutParams();
		if ((rootParams != null) && (rootParams.height != height)) {
			rootParams.height = height;
			root.setLayoutParams(rootParams);
		}
		root.setMinimumHeight(height);

		ImageView artwork = root.findViewById(R.id.dashboard_item_icon);
		int artworkSize = px(root, spec.artworkSizeDp());
		ConstraintLayout.LayoutParams artworkParams =
				(ConstraintLayout.LayoutParams) artwork.getLayoutParams();
		if ((artworkParams.width != artworkSize) || (artworkParams.height != artworkSize)) {
			artworkParams.width = artworkSize;
			artworkParams.height = artworkSize;
		}
		artworkParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
		artworkParams.verticalBias = 0.5F;
		artwork.setLayoutParams(artworkParams);
		int artworkPadding = px(root, spec.artworkPaddingDp());
		artwork.setPadding(artworkPadding, artworkPadding, artworkPadding, artworkPadding);

		View guide = root.findViewById(R.id.dashboard_smart_context_guide);
		ConstraintLayout.LayoutParams guideParams =
				(ConstraintLayout.LayoutParams) guide.getLayoutParams();
		guideParams.guidePercent = spec.showQuickRecent() ? -1F : 1F;
		guideParams.guideEnd = spec.showQuickRecent() ? px(root, spec.recentPanelWidthDp()) :
				ConstraintLayout.LayoutParams.UNSET;
		guide.setLayoutParams(guideParams);

		SmartTopTypographyPolicy.Typography typography = spec.typography();
		TextView eyebrow = root.findViewById(R.id.dashboard_item_eyebrow);
		applyTextRole(root, eyebrow, typography.eyebrowSp(), typography.eyebrowMinHeightDp(), 1);
		TextView title = root.findViewById(R.id.dashboard_item_title);
		applyTextRole(root, title, typography.titleSp(), typography.titleMinHeightDp(),
				typography.titleLines());
		TextView subtitle = root.findViewById(R.id.dashboard_item_subtitle);
		applyTextRole(root, subtitle, typography.subtitleSp(), typography.subtitleMinHeightDp(), 1);
		applySecondaryTextScale(root, environment.fontScale());

		View progress = root.findViewById(R.id.dashboard_smart_progress_group);
		ConstraintLayout.LayoutParams progressParams =
				(ConstraintLayout.LayoutParams) progress.getLayoutParams();
		progressParams.width = 0;
		progressParams.startToEnd = R.id.dashboard_item_icon;
		progressParams.startToStart = ConstraintLayout.LayoutParams.UNSET;
		progressParams.endToStart = R.id.dashboard_item_actions;
		progressParams.endToEnd = ConstraintLayout.LayoutParams.UNSET;
		progressParams.topToBottom = R.id.dashboard_item_subtitle;
		progressParams.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
		progressParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
		progressParams.setMarginStart(px(root, 10));
		progressParams.setMarginEnd(px(root, 8));
		progressParams.bottomMargin = 0;
		progress.setLayoutParams(progressParams);

		// The transport rail is always a peer of metadata, never a second row. Center it against
		// the stable eyebrow -> progress metadata block so larger fonts do not make it drift down.
		View actions = root.findViewById(R.id.dashboard_item_actions);
		ConstraintLayout.LayoutParams actionParams =
				(ConstraintLayout.LayoutParams) actions.getLayoutParams();
		actionParams.startToStart = ConstraintLayout.LayoutParams.UNSET;
		actionParams.startToEnd = ConstraintLayout.LayoutParams.UNSET;
		actionParams.endToStart = R.id.dashboard_smart_context_guide;
		actionParams.setMarginStart(0);
		actionParams.topToTop = R.id.dashboard_item_eyebrow;
		actionParams.topToBottom = ConstraintLayout.LayoutParams.UNSET;
		actionParams.bottomToBottom = R.id.dashboard_smart_progress_group;
		actionParams.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
		actionParams.verticalBias = 0.5F;
		actions.setLayoutParams(actionParams);

		MaterialButton label = root.findViewById(R.id.dashboard_action_label);
		if (spec.terminalActionWidthDp() > 0) {
			label.setMaxWidth(px(root, spec.terminalActionWidthDp()));
		}
		applyActionGeometry(root, label, spec);

		if (root instanceof ViewGroup group) {
			int touchTargetDp = automotive ? Math.max(64, spec.actionCellDp()) : spec.actionCellDp();
			MinimumTouchTargetDelegate.install(group, touchTargetDp,
					label,
					root.findViewById(R.id.dashboard_action_prev),
					root.findViewById(R.id.dashboard_action_play_pause),
					root.findViewById(R.id.dashboard_action_next),
					root.findViewById(R.id.dashboard_action_favorite),
					root.findViewById(R.id.dashboard_action_back_to_list));
		}
		// The XML starts invisible so an unbound baseline cannot flash at the wrong height.
		// Reveal only after the complete measured geometry for this holder is installed.
		root.setVisibility(View.VISIBLE);
		root.setTag(R.id.dashboard_smart_layout_token, token);
	}

	static SmartTopLayoutSpec layoutSpec(View root, SmartTopViewState state) {
		Object tag = root.getTag(R.id.dashboard_smart_layout_token);
		if (tag instanceof LayoutToken token) return token.spec();
		SmartTopEnvironment environment = environment(root, false);
		return SmartTopAdaptivePolicy.resolve(environment, state.actions(),
				contentMetrics(root, state, environment));
	}

	private static SmartTopEnvironment environment(View root, boolean automotive) {
		return new SmartTopEnvironment(measuredWidthDp(root), measuredViewportHeightDp(root),
				root.getResources().getConfiguration().fontScale,
				automotive ? SmartTopInteractionProfile.AUTOMOTIVE : SmartTopInteractionProfile.TOUCH);
	}

	private static SmartTopContentMetrics contentMetrics(View root, SmartTopViewState state,
			SmartTopEnvironment environment) {
		SmartTopLayoutMode mode = SmartTopAdaptivePolicy.resolveMode(environment);
		SmartTopTypographyPolicy.Typography typography =
				SmartTopTypographyPolicy.resolve(mode, environment.fontScale());
		float titleWidthDp = measureTextDp(root, state.title(), typography.titleSp());
		CharSequence terminalLabel = terminalLabel(root, state.actions());
		float terminalWidthDp = measureTextDp(root, terminalLabel,
				SmartTopTypographyPolicy.secondarySp(13F, environment.fontScale()));
		return new SmartTopContentMetrics(titleWidthDp, terminalWidthDp, state.quickRecent().size());
	}

	private static CharSequence terminalLabel(View root, List<SmartTopAction> actions) {
		for (SmartTopAction action : actions) {
			if (action == SmartTopAction.OPEN_ADDONS) return root.getResources().getString(R.string.settings);
			if (action == SmartTopAction.RETRY) return root.getResources().getString(R.string.retry);
		}
		return "";
	}

	private static float measureTextDp(View root, CharSequence text, float textSizeSp) {
		if ((text == null) || (text.length() == 0)) return 0F;
		float density = Math.max(0.1F, root.getResources().getDisplayMetrics().density);
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setTextSize(textSizeSp * root.getResources().getDisplayMetrics().scaledDensity);
		return paint.measureText(text.toString()) / density;
	}

	private static void applyTextRole(View root, TextView view, float textSizeSp,
			int minHeightDp, int lines) {
		view.setMinLines(lines);
		view.setMaxLines(lines);
		view.setIncludeFontPadding(false);
		view.setMinHeight(px(root, minHeightDp));
		view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
	}

	private static void applySecondaryTextScale(View root, float fontScale) {
		setTextSize(root, R.id.dashboard_smart_progress_current,
				SmartTopTypographyPolicy.secondarySp(10F, fontScale));
		setTextSize(root, R.id.dashboard_smart_progress_total,
				SmartTopTypographyPolicy.secondarySp(10F, fontScale));
		setTextSize(root, R.id.dashboard_recent_title,
				SmartTopTypographyPolicy.secondarySp(13F, fontScale));
		setTextSize(root, R.id.dashboard_recent_item_1,
				SmartTopTypographyPolicy.secondarySp(13F, fontScale));
		setTextSize(root, R.id.dashboard_recent_item_2,
				SmartTopTypographyPolicy.secondarySp(13F, fontScale));
		setTextSize(root, R.id.dashboard_recent_item_3,
				SmartTopTypographyPolicy.secondarySp(13F, fontScale));
	}

	private static void setTextSize(View root, int id, float sp) {
		TextView view = root.findViewById(id);
		if (view != null) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
	}

	private static void applyActionGeometry(View root, MaterialButton label,
			SmartTopLayoutSpec spec) {
		int cell = px(root, spec.actionCellDp());
		int gap = px(root, spec.actionGapDp());
		View actions = root.findViewById(R.id.dashboard_item_actions);
		ViewGroup.LayoutParams containerParams = actions.getLayoutParams();
		if ((containerParams != null) && (containerParams.height != cell)) {
			containerParams.height = cell;
			actions.setLayoutParams(containerParams);
		}

		List<SmartTopAction> visible = spec.visibleActions();
		boolean labelActive = false;
		for (SmartTopAction action : visible) {
			if (SmartTopAdaptivePolicy.isLabeled(action)) {
				labelActive = true;
				break;
			}
		}
		LinearLayout.LayoutParams labelParams = (LinearLayout.LayoutParams) label.getLayoutParams();
		labelParams.width = labelActive ? px(root, spec.terminalActionWidthDp()) : 0;
		labelParams.height = cell;
		labelParams.setMarginEnd(labelActive && (SmartTopAdaptivePolicy.componentCount(visible) > 1) ?
				gap : 0);
		label.setLayoutParams(labelParams);
		label.setMinimumWidth(labelActive ? cell : 0);
		label.setMinimumHeight(cell);
		label.setIconSize(px(root, spec.secondaryGlyphDp()));

		List<ImageButton> buttons = List.of(
				root.findViewById(R.id.dashboard_action_prev),
				root.findViewById(R.id.dashboard_action_play_pause),
				root.findViewById(R.id.dashboard_action_next),
				root.findViewById(R.id.dashboard_action_favorite),
				root.findViewById(R.id.dashboard_action_back_to_list));
		for (int slot = 0; slot < buttons.size(); slot++) {
			ImageButton button = buttons.get(slot);
			SmartTopAction action = SmartTopAdaptivePolicy.actionAtSlot(visible, slot);
			LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) button.getLayoutParams();
			params.width = (action == null) ? 0 : cell;
			params.height = cell;
			params.setMarginEnd((action != null) && hasLaterSlot(visible, slot) ? gap : 0);
			button.setLayoutParams(params);
			if (action != null) {
				int glyphDp = ((action == SmartTopAction.PLAY) ||
						(action == SmartTopAction.PLAY_PAUSE)) ? spec.primaryGlyphDp() :
						spec.secondaryGlyphDp();
				int inset = Math.max(0, (cell - px(root, glyphDp)) / 2);
				button.setPadding(inset, inset, inset, inset);
			}
		}
	}

	private static boolean hasLaterSlot(List<SmartTopAction> actions, int slot) {
		for (SmartTopAction action : actions) {
			if (SmartTopAdaptivePolicy.slotIndex(action) > slot) return true;
		}
		return false;
	}

	static float measuredWidthDp(View root) {
		float density = Math.max(0.1F, root.getResources().getDisplayMetrics().density);
		int width = root.getWidth();
		if ((width <= 0) && (root.getParent() instanceof View parent)) width = parent.getWidth();
		return (width > 0) ? width / density :
				root.getResources().getConfiguration().screenWidthDp;
	}

	static float measuredViewportHeightDp(View root) {
		float density = Math.max(0.1F, root.getResources().getDisplayMetrics().density);
		int height = 0;
		if (root.getParent() instanceof View parent) height = parent.getHeight();
		if ((height <= 0) && (root.getRootView() != null)) height = root.getRootView().getHeight();
		return (height > 0) ? height / density :
				root.getResources().getConfiguration().screenHeightDp;
	}

	private static int px(View view, int dp) {
		return UiUtils.toIntPx(view.getContext(), dp);
	}

	private record LayoutToken(SmartTopEnvironment environment, SmartTopLayoutSpec spec) {
	}
}
