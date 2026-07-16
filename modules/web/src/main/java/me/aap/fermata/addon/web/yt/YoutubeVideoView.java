package me.aap.fermata.addon.web.yt;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.ui.view.VideoInfoView;
import me.aap.fermata.ui.view.VideoView;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeVideoView extends VideoView {

	public YoutubeVideoView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void init(Context context) {
		addView(new FrameLayout(context), new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		addInfoView(context);
	}

	@Override
	protected void addInfoView(Context context) {
		VideoInfoView info = new HiddenVideoInfoView(context);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
		lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
		info.setLayoutParams(lp);
		addView(info);
	}

	@NonNull
	@Override
	public VideoInfoView getVideoInfoView() {
		return (VideoInfoView) getChildAt(1);
	}

	@Nullable
	@Override
	public SurfaceView getSubtitleSurface() {
		return null;
	}

	private static final class HiddenVideoInfoView extends VideoInfoView {
		HiddenVideoInfoView(@NonNull Context context) {
			super(context, null);
			super.setVisibility(GONE);
		}

		@Override
		public void setVisibility(int visibility) {
			super.setVisibility(GONE);
		}
	}
}
