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
	private FrameLayout content;
	private VideoInfoView videoInfo;

	public YoutubeVideoView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void init(Context context) {
		content = new FrameLayout(context);
		addView(content, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		addInfoView(context);
	}

	@Override
	protected void addInfoView(Context context) {
		VideoInfoView info = new HiddenVideoInfoView(context);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
		lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
		info.setLayoutParams(lp);
		videoInfo = info;
		addView(info);
	}

	@NonNull
	@Override
	public VideoInfoView getVideoInfoView() {
		return videoInfo;
	}

	public FrameLayout getContentView() {
		return content;
	}

	/** The browser custom view owns rendering; no MediaCodec/VLC decoder Surface is attached. */
	@Nullable
	@Override
	public SurfaceView getVideoSurface() {
		return null;
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
