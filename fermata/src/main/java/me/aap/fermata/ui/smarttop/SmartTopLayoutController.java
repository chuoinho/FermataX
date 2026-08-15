package me.aap.fermata.ui.smarttop;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.TypedValue;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;

import me.aap.fermata.R;
import me.aap.fermata.ui.view.MinimumTouchTargetDelegate;
import me.aap.utils.ui.UiUtils;

/** Applies the measured SmartTop mode without changing the legacy dashboard layout. */
public final class SmartTopLayoutController {
	private SmartTopLayoutController() {
	}

	public static void apply(View root, SmartTopViewState state, boolean automotive) {
		SmartTopLayoutMode mode = state.layout();
		boolean showContext = (mode != SmartTopLayoutMode.COMPACT) &&
				!state.quickRecent().isEmpty();
		LayoutToken token = new LayoutToken(mode, automotive,
				root.getResources().getConfiguration().fontScale, state.actions().size(), showContext);
		if (token.equals(root.getTag(R.id.dashboard_smart_layout_token))) return;
		int padding = px(root, cardPaddingDp(mode, automotive));
		root.setPadding(padding, padding, padding, padding);
		int height = px(root, SmartTopLayoutPolicy.cardHeightDp(mode,
				root.getResources().getConfiguration().fontScale));
		ViewGroup.LayoutParams rootParams = root.getLayoutParams();
		if ((rootParams != null) && (rootParams.height != height)) {
			rootParams.height = height;
			root.setLayoutParams(rootParams);
		}
		root.setMinimumHeight(height);

		ImageView artwork = root.findViewById(R.id.dashboard_item_icon);
		int artworkSize = px(root, artworkSizeDp(mode, automotive));
		ConstraintLayout.LayoutParams artworkParams =
				(ConstraintLayout.LayoutParams) artwork.getLayoutParams();
		if ((artworkParams.width != artworkSize) || (artworkParams.height != artworkSize)) {
			artworkParams.width = artworkSize;
			artworkParams.height = artworkSize;
		}
		boolean compact = mode == SmartTopLayoutMode.COMPACT;
		artworkParams.bottomToBottom = compact ? ConstraintLayout.LayoutParams.UNSET :
				ConstraintLayout.LayoutParams.PARENT_ID;
		artworkParams.verticalBias = 0.5F;
		artwork.setLayoutParams(artworkParams);
		int artworkPadding = px(root, artworkPaddingDp(mode, automotive));
		artwork.setPadding(artworkPadding, artworkPadding, artworkPadding, artworkPadding);

		View guide = root.findViewById(R.id.dashboard_smart_context_guide);
		ConstraintLayout.LayoutParams guideParams =
				(ConstraintLayout.LayoutParams) guide.getLayoutParams();
		guideParams.guidePercent = showContext ? -1F : 1F;
		guideParams.guideEnd = showContext ? px(root, contextPanelWidthDp(mode)) :
				ConstraintLayout.LayoutParams.UNSET;
		guide.setLayoutParams(guideParams);

		TextView title = root.findViewById(R.id.dashboard_item_title);
		title.setMaxLines(1);
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSizeSp(mode));
		TextView eyebrow = root.findViewById(R.id.dashboard_item_eyebrow);
		eyebrow.setTextSize(TypedValue.COMPLEX_UNIT_SP,
				(mode == SmartTopLayoutMode.EXPANDED) ? 14F : 13F);
		TextView subtitle = root.findViewById(R.id.dashboard_item_subtitle);
		subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP,
				(mode == SmartTopLayoutMode.EXPANDED) ? 15F : 14F);

		View actions = root.findViewById(R.id.dashboard_item_actions);
		ConstraintLayout.LayoutParams actionParams =
				(ConstraintLayout.LayoutParams) actions.getLayoutParams();
		actionParams.startToStart = ConstraintLayout.LayoutParams.UNSET;
		actionParams.startToEnd = ConstraintLayout.LayoutParams.UNSET;
		actionParams.endToStart = R.id.dashboard_smart_context_guide;
		actionParams.setMarginStart(0);
		actions.setLayoutParams(actionParams);

		View progress = root.findViewById(R.id.dashboard_smart_progress_group);
		ConstraintLayout.LayoutParams progressParams =
				(ConstraintLayout.LayoutParams) progress.getLayoutParams();
		// Timeline belongs to the primary item, never to the Quick Recent context panel.
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

		MaterialButton label = root.findViewById(R.id.dashboard_action_label);
		label.setMaxWidth(px(root, labeledActionMaxWidthDp(automotive)));

		if (root instanceof ViewGroup group) {
			int minTargetDp = automotive ? mode.automotiveTouchTargetDp() :
					SmartTopLayoutPolicy.MIN_CONTROL_DP;
			MinimumTouchTargetDelegate.install(group, minTargetDp,
					label,
					root.findViewById(R.id.dashboard_action_prev),
					root.findViewById(R.id.dashboard_action_play_pause),
					root.findViewById(R.id.dashboard_action_next),
					root.findViewById(R.id.dashboard_action_favorite),
					root.findViewById(R.id.dashboard_action_back_to_list));
		}
		root.setTag(R.id.dashboard_smart_layout_token, token);
	}

	static int artworkSizeDp(SmartTopLayoutMode mode, boolean automotive) {
		return (!automotive && (mode == SmartTopLayoutMode.COMPACT)) ?
				SmartTopLayoutPolicy.MOBILE_ARTWORK_DP : mode.artworkSizeDp();
	}

	static int labeledActionMaxWidthDp(boolean automotive) {
		return automotive ? SmartTopLayoutPolicy.MAX_LABELED_ACTION_DP :
				SmartTopLayoutPolicy.MAX_MOBILE_LABELED_ACTION_DP;
	}

	static int contextPanelWidthDp(SmartTopLayoutMode mode) {
		return switch (mode) {
			case COMPACT -> 0;
			case STANDARD -> 160;
			case EXPANDED -> 196;
		};
	}

	static int timelineWidthDp(SmartTopLayoutMode mode) {
		return switch (mode) {
			case COMPACT -> 0;
			case STANDARD -> 140;
			case EXPANDED -> 176;
		};
	}

	static int cardPaddingDp(SmartTopLayoutMode mode, boolean automotive) {
		return switch (mode) {
			case COMPACT -> automotive ? 10 : 12;
			case STANDARD -> 14;
			case EXPANDED -> 16;
		};
	}

	static int artworkPaddingDp(SmartTopLayoutMode mode, boolean automotive) {
		return switch (mode) {
			case COMPACT -> automotive ? 13 : 11;
			case STANDARD -> 16;
			case EXPANDED -> 18;
		};
	}

	static float titleSizeSp(SmartTopLayoutMode mode) {
		return switch (mode) {
			case COMPACT -> 19F;
			case STANDARD -> 22F;
			case EXPANDED -> 24F;
		};
	}

	private static int px(View view, int dp) {
		return UiUtils.toIntPx(view.getContext(), dp);
	}

	private record LayoutToken(SmartTopLayoutMode layout, boolean automotive,
			float fontScale, int actionCount, boolean showContext) {
	}
}
