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
		int pad = GlassDimensions.dpToPx(context, 16);
		root.setPadding(pad, pad, pad, pad);

		View div = root.findViewById(com.google.android.material.R.id.titleDividerNoCustom);
		if (div != null) div.setVisibility(View.GONE);

		styleViewHierarchy(root, colors, isNight);

		styleButton(root.findViewById(android.R.id.button1), colors, true, isNight);
		styleButton(root.findViewById(android.R.id.button2), colors, false, isNight);
		styleButton(root.findViewById(android.R.id.button3), colors, false, isNight);
	}

	private void styleButton(View btn, GlassThemeColors colors, boolean positive, boolean isNight) {
		if (btn instanceof TextView) {
			TextView tv = (TextView) btn;
			tv.setAllCaps(false);
			int hPad = GlassDimensions.dpToPx(context, positive ? 16 : 12);
			int vPad = GlassDimensions.dpToPx(context, 8);
			tv.setPadding(hPad, vPad, hPad, vPad);
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
			if (dialog.getWindow() != null && dialog.getWindow().getDecorView() != null) {
				View decor = dialog.getWindow().getDecorView();
				int pad = GlassDimensions.dpToPx(context, 16);
				decor.setPadding(pad, pad, pad, pad);
				styleViewHierarchy(decor, colors, isNight);
			}
			if (dialog instanceof androidx.appcompat.app.AlertDialog ad) {
				Button p = ad.getButton(DialogInterface.BUTTON_POSITIVE);
				if (p != null) {
					p.setTextColor(colors.getActionText());
					p.setBackground(GlassDrawables.createGlassPillDrawable(context, 999f, isNight));
					int hPad = GlassDimensions.dpToPx(context, 16);
					int vPad = GlassDimensions.dpToPx(context, 8);
					p.setPadding(hPad, vPad, hPad, vPad);
				}
				Button n = ad.getButton(DialogInterface.BUTTON_NEGATIVE);
				if (n != null) {
					n.setTextColor(colors.getTextSecondary());
					int hPad = GlassDimensions.dpToPx(context, 12);
					int vPad = GlassDimensions.dpToPx(context, 8);
					n.setPadding(hPad, vPad, hPad, vPad);
				}
				Button neu = ad.getButton(DialogInterface.BUTTON_NEUTRAL);
				if (neu != null) {
					neu.setTextColor(colors.getTextSecondary());
					int hPad = GlassDimensions.dpToPx(context, 12);
					int vPad = GlassDimensions.dpToPx(context, 8);
					neu.setPadding(hPad, vPad, hPad, vPad);
				}
			} else if (dialog instanceof android.app.AlertDialog ad) {
				Button p = ad.getButton(DialogInterface.BUTTON_POSITIVE);
				if (p != null) {
					p.setTextColor(colors.getActionText());
					p.setBackground(GlassDrawables.createGlassPillDrawable(context, 999f, isNight));
					int hPad = GlassDimensions.dpToPx(context, 16);
					int vPad = GlassDimensions.dpToPx(context, 8);
					p.setPadding(hPad, vPad, hPad, vPad);
				}
				Button n = ad.getButton(DialogInterface.BUTTON_NEGATIVE);
				if (n != null) {
					n.setTextColor(colors.getTextSecondary());
					int hPad = GlassDimensions.dpToPx(context, 12);
					int vPad = GlassDimensions.dpToPx(context, 8);
					n.setPadding(hPad, vPad, hPad, vPad);
				}
				Button neu = ad.getButton(DialogInterface.BUTTON_NEUTRAL);
				if (neu != null) {
					neu.setTextColor(colors.getTextSecondary());
					int hPad = GlassDimensions.dpToPx(context, 12);
					int vPad = GlassDimensions.dpToPx(context, 8);
					neu.setPadding(hPad, vPad, hPad, vPad);
				}
			}
		});
	}

	public static void styleViewHierarchy(View view, GlassThemeColors colors, boolean isNight) {
		if (view == null) return;
		if (view instanceof android.widget.EditText et) {
			et.setTextColor(colors.getInputText());
			et.setHintTextColor(colors.getInputHint());
			android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
			inputBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
			inputBg.setCornerRadius(GlassDimensions.dpToPx(view.getContext(), 10f));
			inputBg.setColor(colors.getInputBg());
			inputBg.setStroke(GlassDimensions.dpToPx(view.getContext(), 1f), colors.getCardBorder());
			et.setBackground(inputBg);
			int p = GlassDimensions.dpToPx(view.getContext(), 10);
			et.setPadding(p, p, p, p);
		} else if (view instanceof android.widget.CheckedTextView ctv) {
			android.content.res.ColorStateList ctvColors = new android.content.res.ColorStateList(
					new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
					new int[]{colors.getActionText(), colors.getTextPrimary()}
			);
			ctv.setTextColor(ctvColors);
			ctv.setCheckMarkTintList(android.content.res.ColorStateList.valueOf(colors.getActionText()));
			int padH = GlassDimensions.dpToPx(ctv.getContext(), 16);
			int padV = GlassDimensions.dpToPx(ctv.getContext(), 10);
			ctv.setPadding(padH, padV, padH, padV);
		} else if (view instanceof android.widget.CompoundButton cb) {
			cb.setTextColor(colors.getTextPrimary());
			cb.setButtonTintList(android.content.res.ColorStateList.valueOf(colors.getActionText()));
		} else if (view instanceof android.widget.ListView lv) {
			lv.setDivider(new android.graphics.drawable.ColorDrawable(colors.getDivider()));
			lv.setDividerHeight(1);
			lv.setSelector(GlassDrawables.createGlassItemHighlightDrawable(lv.getContext(), isNight));
			lv.setOnHierarchyChangeListener(new android.view.ViewGroup.OnHierarchyChangeListener() {
				@Override
				public void onChildViewAdded(View parent, View child) {
					styleViewHierarchy(child, colors, isNight);
				}

				@Override
				public void onChildViewRemoved(View parent, View child) {}
			});
			lv.post(() -> {
				for (int i = 0, count = lv.getChildCount(); i < count; i++) {
					styleViewHierarchy(lv.getChildAt(i), colors, isNight);
				}
			});
		} else if (view instanceof TextView tv) {
			int id = tv.getId();
			if (id == androidx.appcompat.R.id.alertTitle || id == com.google.android.material.R.id.alertTitle) {
				tv.setTextColor(colors.getTextPrimary());
				tv.setTextSize(18f);
				tv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
			} else if (id == android.R.id.message) {
				tv.setTextColor(colors.getTextSecondary());
				tv.setTextSize(14f);
			} else if (id != android.R.id.button1 && id != android.R.id.button2 && id != android.R.id.button3) {
				tv.setTextColor(colors.getTextPrimary());
			}
		}

		if (view instanceof android.view.ViewGroup vg && !(view instanceof android.widget.ListView)) {
			for (int i = 0, count = vg.getChildCount(); i < count; i++) {
				styleViewHierarchy(vg.getChildAt(i), colors, isNight);
			}
		}
	}
}
