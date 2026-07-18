package me.aap.fermata.ui.view;

import static android.util.TypedValue.COMPLEX_UNIT_SP;
import static me.aap.fermata.BuildConfig.AUTO;

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
import me.aap.utils.ui.view.ToolBarView;

public class FermataToolBarView extends ToolBarView {
	public FermataToolBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public FermataToolBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
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
		if (!isCarInterface()) return;

		setBackgroundResource(R.drawable.aa_top_bar_bg);
		int horizontalPadding = UiUtils.toIntPx(getContext(), 14);
		setPadding(horizontalPadding, 0, horizontalPadding, 0);

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
