package me.aap.fermata.ui.view;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Map;
import java.util.WeakHashMap;

import me.aap.fermata.R;

/** Keeps every attached list reachable above the overlaid control panel. */
final class ControlPanelContentInsetCoordinator implements ViewTreeObserver.OnGlobalLayoutListener {
	private final Map<RecyclerView, OriginalPadding> lists = new WeakHashMap<>();
	@Nullable
	private ViewGroup host;
	private int insetBottom;

	void setPanelVisible(ViewGroup host, boolean visible) {
		attach(host);
		insetBottom = visible ? host.getResources().getDimensionPixelSize(
				R.dimen.control_panel_height) : 0;
		applyInsets();
	}

	void release() {
		ViewGroup current = host;
		if ((current != null) && current.getViewTreeObserver().isAlive()) {
			current.getViewTreeObserver().removeOnGlobalLayoutListener(this);
		}
		for (Map.Entry<RecyclerView, OriginalPadding> entry : lists.entrySet()) {
			RecyclerView list = entry.getKey();
			OriginalPadding padding = entry.getValue();
			if ((list == null) || (padding == null)) continue;
			list.setClipToPadding(padding.clipToPadding);
			list.setPadding(padding.left, padding.top, padding.right, padding.bottom);
		}
		lists.clear();
		host = null;
		insetBottom = 0;
	}

	@Override
	public void onGlobalLayout() {
		applyInsets();
	}

	static int bottomPadding(int originalBottom, int panelInset) {
		return Math.max(0, originalBottom) + Math.max(0, panelInset);
	}

	private void attach(ViewGroup next) {
		if (host == next) return;
		ViewGroup current = host;
		if ((current != null) && current.getViewTreeObserver().isAlive()) {
			current.getViewTreeObserver().removeOnGlobalLayoutListener(this);
		}
		host = next;
		next.getViewTreeObserver().addOnGlobalLayoutListener(this);
	}

	private void applyInsets() {
		ViewGroup root = host;
		if (root != null) visit(root);
	}

	private void visit(View view) {
		if (view instanceof RecyclerView list) updateList(list);
		if (!(view instanceof ViewGroup group)) return;
		for (int i = 0, count = group.getChildCount(); i < count; i++) visit(group.getChildAt(i));
	}

	private void updateList(RecyclerView list) {
		OriginalPadding padding = lists.get(list);
		if (padding == null) {
			padding = new OriginalPadding(list.getPaddingLeft(), list.getPaddingTop(),
					list.getPaddingRight(), list.getPaddingBottom(), list.getClipToPadding());
			lists.put(list, padding);
		} else {
			int expectedBottom = bottomPadding(padding.bottom, padding.appliedInset);
			if (list.getPaddingBottom() != expectedBottom) {
				padding.bottom = Math.max(0, list.getPaddingBottom());
			}
		}

		int bottom = bottomPadding(padding.bottom, insetBottom);
		if ((list.getPaddingLeft() != padding.left) || (list.getPaddingTop() != padding.top) ||
				(list.getPaddingRight() != padding.right) || (list.getPaddingBottom() != bottom)) {
			list.setPadding(padding.left, padding.top, padding.right, bottom);
		}
		boolean clip = (insetBottom == 0) && padding.clipToPadding;
		if (list.getClipToPadding() != clip) list.setClipToPadding(clip);
		padding.appliedInset = insetBottom;
	}

	private static final class OriginalPadding {
		final int left;
		final int top;
		final int right;
		final boolean clipToPadding;
		int bottom;
		int appliedInset;

		OriginalPadding(int left, int top, int right, int bottom, boolean clipToPadding) {
			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;
			this.clipToPadding = clipToPadding;
		}
	}
}
