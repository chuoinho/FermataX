package me.aap.fermata.ui.glass.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
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
		boolean isNight = GlassThemeColors.isNight(context);
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
				tv.setTextColor(colors.getActionText());
				tv.setBackground(GlassDrawables.createGlassPillDrawable(context, 999f, isNight));
			} else {
				tv.setTextColor(colors.getTextSecondary());
			}
		}
	}

	public static void styleAlertDialog(android.app.Dialog dialog, Context context) {
		if (dialog == null || context == null) return;
		boolean isNight = GlassThemeColors.isNight(context);
		GlassThemeColors colors = GlassThemeColors.get(context, isNight);
		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawable(GlassDrawables.createGlassCardDrawable(context, 20f, isNight));
		}
		dialog.setOnShowListener(d -> {
			if (dialog instanceof androidx.appcompat.app.AlertDialog) {
				androidx.appcompat.app.AlertDialog ad = (androidx.appcompat.app.AlertDialog) dialog;
				Button p = ad.getButton(DialogInterface.BUTTON_POSITIVE);
				if (p != null) {
					p.setTextColor(colors.getActionText());
					p.setBackground(GlassDrawables.createGlassPillDrawable(context, 999f, isNight));
				}
				Button n = ad.getButton(DialogInterface.BUTTON_NEGATIVE);
				if (n != null) n.setTextColor(colors.getTextSecondary());
				Button neu = ad.getButton(DialogInterface.BUTTON_NEUTRAL);
				if (neu != null) neu.setTextColor(colors.getTextSecondary());
			} else if (dialog instanceof android.app.AlertDialog) {
				android.app.AlertDialog ad = (android.app.AlertDialog) dialog;
				Button p = ad.getButton(DialogInterface.BUTTON_POSITIVE);
				if (p != null) {
					p.setTextColor(colors.getActionText());
					p.setBackground(GlassDrawables.createGlassPillDrawable(context, 999f, isNight));
				}
				Button n = ad.getButton(DialogInterface.BUTTON_NEGATIVE);
				if (n != null) n.setTextColor(colors.getTextSecondary());
				Button neu = ad.getButton(DialogInterface.BUTTON_NEUTRAL);
				if (neu != null) neu.setTextColor(colors.getTextSecondary());
			}
		});
	}
}
