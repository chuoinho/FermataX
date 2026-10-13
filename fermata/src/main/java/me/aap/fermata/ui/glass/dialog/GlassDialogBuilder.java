package me.aap.fermata.ui.glass.dialog;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.widget.TextView;

import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassHardwareUtils;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.DialogView;

public class GlassDialogBuilder extends DialogView.Builder {
	private final Context context;
	private final OverlayMenu menu;

	public GlassDialogBuilder(Context context, OverlayMenu menu) {
		super(context);
		this.context = context;
		this.menu = menu;
	}

	@Override
	public void show() {
		OverlayMenu m = (menu != null) ? menu : me.aap.utils.ui.activity.ActivityDelegate.get(context).getActiveMenu();
		if (m != null) {
			m.show(b -> {
				View v = build(m::hide);
				styleGlassDialog(v);
				b.setView(v);
			});
		}
	}

	private void styleGlassDialog(View root) {
		if (root == null) return;
		boolean isNight = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
		GlassThemeColors colors = GlassThemeColors.get(context, isNight);

		root.setBackground(GlassDrawables.createGlassCardDrawable(context, 20f, isNight));
		GlassHardwareUtils.clipRound(root, GlassDimensions.dpToPx(context, 20f));

		View title = root.findViewById(androidx.appcompat.R.id.alertTitle);
		if (title instanceof TextView) {
			((TextView) title).setTextColor(colors.getTextPrimary());
		}

		View msg = root.findViewById(android.R.id.message);
		if (msg instanceof TextView) {
			((TextView) msg).setTextColor(colors.getTextSecondary());
		}

		styleButton(root.findViewById(android.R.id.button1), colors, true, isNight);
		styleButton(root.findViewById(android.R.id.button2), colors, false, isNight);
		styleButton(root.findViewById(android.R.id.button3), colors, false, isNight);
	}

	private void styleButton(View btn, GlassThemeColors colors, boolean positive, boolean isNight) {
		if (btn instanceof TextView) {
			TextView tv = (TextView) btn;
			tv.setAllCaps(false);
			if (positive) {
				tv.setTextColor(GlassThemeColors.ACCENT_CYAN);
				tv.setBackground(GlassDrawables.createGlassPillDrawable(context, 999f, isNight));
			} else {
				tv.setTextColor(colors.getTextSecondary());
			}
		}
	}
}
