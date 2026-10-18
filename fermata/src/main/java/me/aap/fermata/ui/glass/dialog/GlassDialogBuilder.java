package me.aap.fermata.ui.glass.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

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
		if (menu instanceof View mv) mv.setBackgroundColor(android.graphics.Color.TRANSPARENT);
		styleGlassDialog(root, context, GlassThemeColors.isNight(context));
	}

	public static void styleGlassDialog(View root, Context context, boolean isNight) {
		if (root == null || context == null) return;
		GlassThemeColors colors = GlassThemeColors.get(context, isNight);

		root.setBackground(GlassDrawables.createGlassDialogDrawable(context, 20f, isNight));
		GlassHardwareUtils.clipRound(root, GlassDimensions.dpToPx(context, 20f));
		int pad = GlassDimensions.dpToPx(context, 12);
		root.setPadding(pad, pad, pad, pad);
		root.setElevation(GlassDimensions.dpToPx(context, 16));

		me.aap.fermata.ui.activity.MainActivityDelegate mad = me.aap.fermata.ui.activity.MainActivityDelegate.get(context);
		boolean isCar = (mad != null) && mad.isCarActivity();
		int minW = GlassDimensions.dpToPx(context, isCar ? 300 : 260);
		root.setMinimumWidth(minW);

		if (root.getParent() instanceof View pv) pv.setBackgroundColor(android.graphics.Color.TRANSPARENT);

		View div = root.findViewById(com.google.android.material.R.id.titleDividerNoCustom);
		if (div != null) div.setVisibility(View.GONE);

		styleViewHierarchy(root, colors, isNight);

		styleButton(context, root.findViewById(android.R.id.button1), colors, true, isNight);
		styleButton(context, root.findViewById(android.R.id.button2), colors, false, isNight);
		styleButton(context, root.findViewById(android.R.id.button3), colors, false, isNight);
	}

	public static void styleButton(Context context, View btn, GlassThemeColors colors, boolean positive, boolean isNight) {
		if (btn instanceof TextView) {
			TextView tv = (TextView) btn;
			tv.setAllCaps(false);
			int hPad = GlassDimensions.dpToPx(context, positive ? 14 : 12);
			int vPad = GlassDimensions.dpToPx(context, 6);
			tv.setPadding(hPad, vPad, hPad, vPad);
			me.aap.fermata.ui.activity.MainActivityDelegate a = me.aap.fermata.ui.activity.MainActivityDelegate.get(context);
			boolean isCar = (a != null) && a.isCarActivity();
			tv.setMinimumHeight(GlassDimensions.dpToPx(context, isCar ? 44 : 38));
			if (positive) {
				tv.setTextColor(colors.getActionText());
				tv.setBackground(GlassDrawables.createGlassPillDrawable(context, 999f, isNight));
			} else {
				tv.setTextColor(colors.getTextSecondary());
				tv.setBackground(GlassDrawables.createGlassItemHighlightDrawable(context, isNight));
			}
		}
	}

	public static void styleAlertDialog(android.app.Dialog dialog, Context context) {
		styleAlertDialog(dialog, context, null);
	}

	public static void styleAlertDialog(android.app.Dialog dialog, Context context, @Nullable android.content.DialogInterface.OnShowListener extraListener) {
		if (dialog == null || context == null) return;
		boolean isNight = GlassThemeColors.isNight(context);
		GlassThemeColors colors = GlassThemeColors.get(context, isNight);
		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawable(GlassDrawables.createGlassDialogDrawable(context, 20f, isNight));
		}
		boolean isCar = GlassDimensions.isCar(context);
		int minBtnH = GlassDimensions.dpToPx(context, isCar ? 44 : 38);

		dialog.setOnShowListener(d -> {
			if (dialog.getWindow() != null && dialog.getWindow().getDecorView() != null) {
				View decor = dialog.getWindow().getDecorView();
				int pad = GlassDimensions.dpToPx(context, 12);
				decor.setPadding(pad, pad, pad, pad);
				styleViewHierarchy(decor, colors, isNight);
			}
			if (dialog instanceof androidx.appcompat.app.AlertDialog ad) {
				updateDialogButton(ad.getButton(DialogInterface.BUTTON_POSITIVE), colors, minBtnH, true, isNight, context);
				updateDialogButton(ad.getButton(DialogInterface.BUTTON_NEGATIVE), colors, minBtnH, false, isNight, context);
				updateDialogButton(ad.getButton(DialogInterface.BUTTON_NEUTRAL), colors, minBtnH, false, isNight, context);
			} else if (dialog instanceof android.app.AlertDialog ad) {
				updateDialogButton(ad.getButton(DialogInterface.BUTTON_POSITIVE), colors, minBtnH, true, isNight, context);
				updateDialogButton(ad.getButton(DialogInterface.BUTTON_NEGATIVE), colors, minBtnH, false, isNight, context);
				updateDialogButton(ad.getButton(DialogInterface.BUTTON_NEUTRAL), colors, minBtnH, false, isNight, context);
			}
			if (extraListener != null) {
				extraListener.onShow(d);
			}
		});
	}

	public static void refreshAlertDialog(android.app.Dialog dialog, Context context, boolean isNight) {
		if (dialog == null || context == null || !dialog.isShowing()) return;
		GlassThemeColors colors = GlassThemeColors.get(context, isNight);
		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawable(GlassDrawables.createGlassDialogDrawable(context, 20f, isNight));
			View decor = dialog.getWindow().getDecorView();
			if (decor != null) {
				styleViewHierarchy(decor, colors, isNight);
			}
		}
		boolean isCar = GlassDimensions.isCar(context);
		int minBtnH = GlassDimensions.dpToPx(context, isCar ? 44 : 38);
		if (dialog instanceof androidx.appcompat.app.AlertDialog ad) {
			updateDialogButton(ad.getButton(DialogInterface.BUTTON_POSITIVE), colors, minBtnH, true, isNight, context);
			updateDialogButton(ad.getButton(DialogInterface.BUTTON_NEGATIVE), colors, minBtnH, false, isNight, context);
			updateDialogButton(ad.getButton(DialogInterface.BUTTON_NEUTRAL), colors, minBtnH, false, isNight, context);
		} else if (dialog instanceof android.app.AlertDialog ad) {
			updateDialogButton(ad.getButton(DialogInterface.BUTTON_POSITIVE), colors, minBtnH, true, isNight, context);
			updateDialogButton(ad.getButton(DialogInterface.BUTTON_NEGATIVE), colors, minBtnH, false, isNight, context);
			updateDialogButton(ad.getButton(DialogInterface.BUTTON_NEUTRAL), colors, minBtnH, false, isNight, context);
		}
	}

	private static void updateDialogButton(Button btn, GlassThemeColors colors, int minBtnH, boolean positive, boolean isNight, Context context) {
		if (btn == null) return;
		btn.setTextColor(positive ? colors.getActionText() : colors.getTextSecondary());
		btn.setBackground(positive ?
				GlassDrawables.createGlassPillDrawable(context, 999f, isNight) :
				GlassDrawables.createGlassItemHighlightDrawable(context, isNight));
		int hPad = GlassDimensions.dpToPx(context, 14);
		int vPad = GlassDimensions.dpToPx(context, 6);
		btn.setPadding(hPad, vPad, hPad, vPad);
		btn.setMinimumHeight(minBtnH);
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
			ctv.setTextSize(15f);
			int padH = GlassDimensions.dpToPx(ctv.getContext(), 14);
			int padV = GlassDimensions.dpToPx(ctv.getContext(), 8);
			ctv.setPadding(padH, padV, padH, padV);
			Context ctx = ctv.getContext();
			boolean isCar = GlassDimensions.isCar(ctx);
			ctv.setMinimumHeight(GlassDimensions.dpToPx(ctx, isCar ? 44 : 40));
		} else if (view instanceof android.widget.CompoundButton cb) {
			cb.setTextColor(colors.getTextPrimary());
			cb.setButtonTintList(android.content.res.ColorStateList.valueOf(colors.getActionText()));
		} else if (view instanceof android.widget.ListView lv) {
			lv.setDivider(new android.graphics.drawable.ColorDrawable(colors.getDivider()));
			lv.setDividerHeight(1);
			lv.setSelector(GlassDrawables.createGlassItemHighlightDrawable(lv.getContext(), isNight));
			android.view.ViewGroup.LayoutParams lp = lv.getLayoutParams();
			if (lp != null && lp.width != android.view.ViewGroup.LayoutParams.MATCH_PARENT) {
				lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
				lv.setLayoutParams(lp);
			}
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
				tv.setTextSize(16f);
				tv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
				int padH = GlassDimensions.dpToPx(tv.getContext(), 14);
				int padV = GlassDimensions.dpToPx(tv.getContext(), 8);
				tv.setPadding(padH, padV, padH, padV);
			} else if (id == android.R.id.message) {
				tv.setTextColor(colors.getTextSecondary());
				tv.setTextSize(13f);
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
