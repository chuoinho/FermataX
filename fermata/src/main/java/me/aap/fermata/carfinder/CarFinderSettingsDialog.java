package me.aap.fermata.carfinder;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import java.io.File;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * Self-contained settings dialog for Car Finder, ensuring no settings leak
 * into the main FermataX global settings page.
 * Styled with FermataX's signature dark navy & electric blue theme,
 * upgraded with Glassmorphism when Glass Theme is active.
 */
public final class CarFinderSettingsDialog {
	public interface OnSettingsChangeListener {
		void onSettingsChanged();
		void onLocationCleared();
	}

	private CarFinderSettingsDialog() {
	}

	public static boolean isGlassTheme(Context context) {
		try {
			MainActivityDelegate a = MainActivityDelegate.get(context);
			return a != null && a.getPrefs().isGlassTheme(a.isCarActivity());
		} catch (Throwable e) {
			return false;
		}
	}

	public static void show(Context context, OnSettingsChangeListener listener) {
		displaySettingsDialog(context, listener);
	}

	public static void displaySettingsDialog(Context context, OnSettingsChangeListener listener) {
		Dialog dialog = new Dialog(context);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

		float density = context.getResources().getDisplayMetrics().density;
		int pad24 = Math.round(24 * density);
		int pad16 = Math.round(16 * density);
		int pad12 = Math.round(12 * density);

		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);

		boolean isGlass = isGlassTheme(context);
		if (isGlass) {
			boolean isNight = me.aap.fermata.ui.glass.theme.GlassThemeColors.isNight(context);
			root.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassCardDrawable(context, 20f, isNight));
		} else {
			GradientDrawable rootBg = new GradientDrawable();
			rootBg.setColor(0xFF131926); // Fermata dark surface
			rootBg.setCornerRadius(16f * density);
			rootBg.setStroke(Math.round(1f * density), 0xFF263552); // Fermata subtle border
			root.setBackground(rootBg);
		}
		root.setPadding(pad24, pad24, pad24, pad24);

		// Header
		TextView title = new TextView(context);
		title.setText(R.string.car_finder_settings_title);
		title.setTextSize(20);
		title.setTextColor(0xFFFFFFFF);
		title.setTypeface(null, Typeface.BOLD);
		root.addView(title);

		View divider1 = createSettingsDividerView(context, pad16);
		root.addView(divider1);

		// Switch 1: Auto-save on disconnect
		SwitchCompat switchAutoSave = new SwitchCompat(context);
		switchAutoSave.setText(R.string.car_finder_pref_auto_save);
		switchAutoSave.setTextColor(0xFFE2E8F0);
		switchAutoSave.setTextSize(15);
		switchAutoSave.setChecked(CarFinderPrefs.isAutoSaveEnabled(context));
		switchAutoSave.setOnCheckedChangeListener((btn, checked) -> {
			CarFinderPrefs.setAutoSaveEnabled(context, checked);
			if (listener != null) listener.onSettingsChanged();
		});
		LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p1.bottomMargin = pad12;
		root.addView(switchAutoSave, p1);

		// Switch 2: Notification on disconnect
		SwitchCompat switchNotif = new SwitchCompat(context);
		switchNotif.setText(R.string.car_finder_pref_notif);
		switchNotif.setTextColor(0xFFE2E8F0);
		switchNotif.setTextSize(15);
		switchNotif.setChecked(CarFinderPrefs.isNotifOnDisconnectEnabled(context));
		switchNotif.setOnCheckedChangeListener((btn, checked) -> {
			CarFinderPrefs.setNotifOnDisconnectEnabled(context, checked);
			if (listener != null) listener.onSettingsChanged();
		});
		LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p2.bottomMargin = pad16;
		root.addView(switchNotif, p2);

		if (isGlass) {
			android.content.res.ColorStateList trackColors = new android.content.res.ColorStateList(new int[][]{
					new int[]{android.R.attr.state_checked}, new int[]{}
			}, new int[]{0x6600E5FF, 0x33FFFFFF});
			switchAutoSave.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFF00E5FF));
			switchAutoSave.setTrackTintList(trackColors);
			switchNotif.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFF00E5FF));
			switchNotif.setTrackTintList(trackColors);
		}

		View divider2 = createSettingsDividerView(context, pad12);
		root.addView(divider2);

		// Action: Clear current parking location
		TextView clearBtn = new TextView(context);
		clearBtn.setText(R.string.car_finder_clear_location);
		clearBtn.setTextColor(0xFFFF5252); // Red warning accent
		clearBtn.setTextSize(15);
		clearBtn.setPadding(0, pad12, 0, pad12);
		clearBtn.setOnClickListener(v -> {
			AlertDialog confirmDialog = new AlertDialog.Builder(context)
					.setTitle(R.string.car_finder_clear_confirm_title)
					.setMessage(R.string.car_finder_clear_confirm_msg)
					.setPositiveButton(android.R.string.ok, (d, w) -> {
						CarParkingLocation loc = CarFinderPrefs.getParkingLocation(context);
						if (loc != null && loc.getPhotoPath() != null) {
							try {
								new File(loc.getPhotoPath()).delete();
							} catch (Throwable ignored) {
							}
						}
						CarLocatorManager.get().clearParkingLocationOnCarFound(context);
						if (listener != null) listener.onLocationCleared();
						dialog.dismiss();
					})
					.setNegativeButton(android.R.string.cancel, null)
					.create();
			if (isGlass) {
				me.aap.fermata.ui.glass.dialog.GlassDialogBuilder.styleAlertDialog(confirmDialog, context);
			}
			confirmDialog.show();
		});
		root.addView(clearBtn);

		// Close button
		TextView closeBtn = new TextView(context);
		boolean isNight = me.aap.fermata.ui.glass.theme.GlassThemeColors.isNight(context);
		closeBtn.setTextColor(isGlass ? (isNight ? 0xFF00E5FF : 0xFF007A87) : 0xFF3B82F6);
		closeBtn.setTextSize(15);
		closeBtn.setTypeface(null, Typeface.BOLD);
		closeBtn.setGravity(Gravity.END);
		closeBtn.setPadding(0, pad16, 0, 0);
		closeBtn.setOnClickListener(v -> dialog.dismiss());
		root.addView(closeBtn);

		dialog.setContentView(root);
		Window window = dialog.getWindow();
		if (window != null) {
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			window.setLayout(Math.round(340 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
		}
		dialog.show();
	}

	private static View createSettingsDividerView(Context context, int marginVertical) {
		View div = new View(context);
		div.setBackgroundColor(0x33263552);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
		lp.topMargin = marginVertical;
		lp.bottomMargin = marginVertical;
		div.setLayoutParams(lp);
		return div;
	}
}
