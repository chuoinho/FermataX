package me.aap.fermata.addon.stremio.ui;

import android.content.Context;
import android.util.AttributeSet;

import me.aap.fermata.ui.view.MediaItemView;

/** MediaItemView-compatible row that is independent of the app-wide grid preference. */
public final class StremioListItemView extends MediaItemView {
	public StremioListItemView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void applyLayout(Context context, boolean grid, float size) {
		removeAllViews();
		inflate(context, me.aap.fermata.R.layout.media_item_list_layout, this);
		getCheckBox().setOnCheckedChangeListener(this);
		setSize(context, false, size);
	}

	@Override
	public void setSize(Context context, boolean grid, float size) {
		super.setSize(context, false, size);
	}
}
