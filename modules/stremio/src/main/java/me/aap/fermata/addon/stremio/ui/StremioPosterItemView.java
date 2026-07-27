package me.aap.fermata.addon.stremio.ui;

import android.content.Context;
import android.util.AttributeSet;

import me.aap.fermata.addon.stremio.R;
import me.aap.fermata.ui.view.MediaItemView;

/** MediaItemView-compatible 2:3 poster card used only by Stremio catalog results. */
public final class StremioPosterItemView extends MediaItemView {
	public StremioPosterItemView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void applyLayout(Context context, boolean grid, float size) {
		removeAllViews();
		inflate(context, R.layout.stremio_poster_item_content, this);
		getCheckBox().setOnCheckedChangeListener(this);
		setSize(context, true, size);
	}

	@Override
	public void setSize(Context context, boolean grid, float size) {
		super.setSize(context, true, size);
	}
}
