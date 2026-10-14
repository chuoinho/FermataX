package me.aap.fermata.ui.view;

import static android.util.TypedValue.COMPLEX_UNIT_SP;
import static me.aap.fermata.BuildConfig.AUTO;
import static me.aap.utils.ui.activity.ActivityListener.ACTIVITY_DESTROY;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

public class FermataToolBarView extends ToolBarView {
	public FermataToolBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public FermataToolBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		applyGlassThemeIfNeeded();
		// Base construction may attach a fragment mediator before MainActivityDelegate stores this view.
		// Reconcile canonical Back/title state once the real toolbar is attached to the activity tree.
		post(this::refreshCanonicalTopBar);
	}

	public void applyGlassThemeIfNeeded() {
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		if (a.getPrefs().isGlassTheme(a.isCarActivity())) {
			Context ctx = getContext();
			boolean isNight = a.getPrefs().isGlassNightMode();
			setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassCardDrawable(ctx, 0f, isNight));
			me.aap.fermata.ui.glass.theme.GlassThemeColors colors =
					me.aap.fermata.ui.glass.theme.GlassThemeColors.get(ctx, isNight);
			styleToolbarChildren(this, colors, ctx, isNight);
		}
	}

	private static void styleToolbarChildren(ViewGroup parent, me.aap.fermata.ui.glass.theme.GlassThemeColors colors, Context ctx, boolean isNight) {
		for (int i = 0, count = parent.getChildCount(); i < count; i++) {
			View child = parent.getChildAt(i);
			if (child instanceof android.widget.EditText et) {
				et.setTextColor(colors.getInputText());
				et.setHintTextColor(colors.getInputHint());
			} else if (child instanceof TextView text) {
				text.setTextColor(colors.getTextPrimary());
				text.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
			} else if (child instanceof android.widget.ImageView iv) {
				iv.setColorFilter(colors.getTextPrimary());
				iv.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassItemHighlightDrawable(ctx, isNight));
			} else if (child instanceof ViewGroup vg) {
				styleToolbarChildren(vg, colors, ctx, isNight);
			}
		}
	}

	@Override
	public void onViewAdded(View child) {
		super.onViewAdded(child);
		applyGlassThemeIfNeeded();
	}

	@Override
	public void onActivityEvent(ActivityDelegate activity, long event) {
		super.onActivityEvent(activity, event);
		if (event != ACTIVITY_DESTROY) refreshCanonicalTopBar();
	}

	private void refreshCanonicalTopBar() {
		ActivityFragment fragment = getActiveFragment();
		Mediator mediator = getMediator();
		if ((fragment == null) || (mediator == null)) return;
		if ((mediator instanceof Mediator.BackTitle) &&
				((findViewById(me.aap.utils.R.id.tool_bar_back_button) == null) ||
						(findViewById(me.aap.utils.R.id.tool_bar_title) == null))) {
			// A retained mediator can survive a host-view recreation after its children were removed.
			// Re-enable the complete mediator so fragment actions are restored with Back/title.
			mediator.disable(this);
			mediator.enable(this, fragment);
		}
		if (findViewById(me.aap.utils.R.id.tool_bar_back_button) == null)
			TopBarMediatorSupport.installBackButton(this, mediator);
		MainActivityDelegate activity = MainActivityDelegate.get(getContext());
		if (!activity.isBarsHidden()) refreshMediatorVisibility();
		TopBarController.refresh(MainActivityDelegate.get(getContext()), fragment);
	}

	@Override
	public void refreshMediatorVisibility() {
		super.refreshMediatorVisibility();
		ActivityFragment fragment = getActiveFragment();
		if (fragment != null) TopBarController.applyVisibility(this, fragment);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		applyGlassThemeIfNeeded();
		if (!(getParent() instanceof ViewGroup parent)) return;
		View[] buttons = new View[getChildCount()];
		int count = 0;
		for (int i = 0; i < getChildCount(); i++) {
			View child = getChildAt(i);
			if (child instanceof ImageButton) buttons[count++] = child;
		}
		if (count != 0)
			MinimumTouchTargetDelegate.install(parent,
					java.util.Arrays.copyOf(buttons, count));
	}

	@Override
	public void setSize(float scale) {
		super.setSize(scale);
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		boolean isGlass = a.getPrefs().isGlassTheme(a.isCarActivity());
		if (isGlass) {
			Context ctx = getContext();
			boolean isNight = a.getPrefs().isGlassNightMode();
			setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassCardDrawable(ctx, 0f, isNight));
			int horizontalPadding = UiUtils.toIntPx(ctx, 14);
			setPadding(horizontalPadding, 0, horizontalPadding, 0);
		} else if (isCarInterface()) {
			setBackgroundResource(R.drawable.aa_top_bar_bg);
			int horizontalPadding = UiUtils.toIntPx(getContext(), 14);
			setPadding(horizontalPadding, 0, horizontalPadding, 0);
		} else {
			return;
		}

		int height = getLayoutParams().height;
		float heightDp = height / getResources().getDisplayMetrics().density;
		float titleSize = (heightDp < 46F) ? 18F : ((heightDp >= 51F) ? 21F : 20F);
		int buttonSize = Math.max(0, height - UiUtils.toIntPx(getContext(), 4));

		for (int i = 0, count = getChildCount(); i < count; i++) {
			View child = getChildAt(i);
			if (child instanceof TextView text) {
				text.setTextSize(COMPLEX_UNIT_SP, titleSize);
			} else if (child instanceof ImageButton button) {
				boolean dashboardSettings = button.getId() == R.id.dashboard_settings;
				int childSize = dashboardSettings ?
						Math.max(0, height - UiUtils.toIntPx(getContext(), 8)) : buttonSize;
				int iconSize = UiUtils.toIntPx(getContext(), dashboardSettings ? 28 : 30);
				int iconPadding = Math.max(0, (childSize - iconSize) / 2);
				ViewGroup.LayoutParams lp = button.getLayoutParams();
				lp.width = childSize;
				lp.height = childSize;
				button.setLayoutParams(lp);
				button.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
			}
		}
	}

	private boolean isCarInterface() {
		return AUTO && MainActivityDelegate.get(getContext()).isCarActivity();
	}
}
