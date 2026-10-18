package me.aap.fermata.ui.glass.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.ControlOnlyPresentation;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;

/**
 * Frosted glass automotive media player card with album art, scrub timeline, and transport controls.
 */
public class GlassMediaPlayerView extends FrameLayout {

    private boolean isNight;
    private GlassThemeColors colors;

    private TextView tvHeader;
    private ImageView ivAlbumArt;
    private TextView tvTrackTitle;
    private TextView tvArtist;
    private LinearLayout timelineLayout;
    private TextView tvCurrentTime;
    private TextView tvTotalDuration;
    private ProgressBar progressBar;

    private LinearLayout transportBar;
    private TextView btnPrev;
    private TextView btnPlayPause;
    private TextView btnNext;
    private TextView btnFavorite;

    public interface OnBrowseMediaClickListener {
        void onBrowseMediaClick();
    }

    public interface OnMediaCardClickListener {
        void onBrowseMedia();
        void onResumeRecent();
        void onOpenPlayingItem(MediaLib.PlayableItem item);
        void onOpenControlOnly(ControlOnlyPresentation controlOnly);
    }

    private OnBrowseMediaClickListener browseMediaClickListener;
    private OnMediaCardClickListener cardClickListener;
    private GlassMediaPlayerController controller;

    public void setOnBrowseMediaClickListener(OnBrowseMediaClickListener listener) {
        this.browseMediaClickListener = listener;
    }

    public void setOnMediaCardClickListener(OnMediaCardClickListener listener) {
        this.cardClickListener = listener;
    }

    public GlassMediaPlayerView(@NonNull Context context) {
        this(context, null);
    }

    public GlassMediaPlayerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlassMediaPlayerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.isNight = GlassThemeColors.isNight(context);
        this.colors = GlassThemeColors.get(context, isNight);
        initView(context);
    }

    private void initView(Context context) {
        setBackground(GlassDrawables.createGlassCardDrawable(context, GlassDimensions.CARD_RADIUS_DP, isNight));
        int padH = GlassDimensions.dpToPx(context, 12);
        int padV = GlassDimensions.dpToPx(context, 10);
        setPadding(padH, padV, padH, padV);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        contentLayout.setLayoutParams(lp);

        // Header
        tvHeader = new TextView(context);
        tvHeader.setText(safeGetString(R.string.glass_media_now_playing, "NOW PLAYING"));
        tvHeader.setTextSize(11f);
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeader.setTextColor(colors.getTextSecondary());
        tvHeader.setLetterSpacing(0.08f);
        contentLayout.addView(tvHeader);

        // Center: Track Artwork & Info
        LinearLayout centerLayout = new LinearLayout(context);
        centerLayout.setOrientation(LinearLayout.HORIZONTAL);
        centerLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        centerLayout.setLayoutParams(centerLp);

        // Album Art
        ivAlbumArt = new ImageView(context);
        int artSize = GlassDimensions.dpToPx(context, 52);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(artSize, artSize);
        artLp.rightMargin = GlassDimensions.dpToPx(context, 10);
        ivAlbumArt.setLayoutParams(artLp);
        ivAlbumArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivAlbumArt.setAdjustViewBounds(false);
        ivAlbumArt.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.ICON_RADIUS_DP, isNight));
        centerLayout.addView(ivAlbumArt);

        // Track & Artist text
        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        textLayout.setLayoutParams(textLp);

        tvTrackTitle = new TextView(context);
        tvTrackTitle.setText(safeGetString(R.string.glass_media_no_playing, "No Media Playing"));
        tvTrackTitle.setTextSize(16f);
        tvTrackTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTrackTitle.setTextColor(colors.getTextPrimary());
        tvTrackTitle.setMaxLines(2);
        tvTrackTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textLayout.addView(tvTrackTitle);

        tvArtist = new TextView(context);
        tvArtist.setText("FermataX");
        tvArtist.setTextSize(13f);
        tvArtist.setTextColor(colors.getTextSecondary());
        tvArtist.setMaxLines(1);
        textLayout.addView(tvArtist);

        centerLayout.addView(textLayout);
        centerLayout.setOnClickListener(v -> handleCardClick());
        contentLayout.addView(centerLayout);

        // Timeline Progress Bar
        timelineLayout = new LinearLayout(context);
        timelineLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlLp.bottomMargin = GlassDimensions.dpToPx(context, 4);
        timelineLayout.setLayoutParams(tlLp);

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgress(0);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, GlassDimensions.dpToPx(context, 8)));
        progressBar.setOnTouchListener((v, event) -> {
            if (controller == null || !controller.isSeekable()) return false;
            float w = v.getWidth();
            if (w <= 0) return false;
            int action = event.getAction();
            if (action == android.view.MotionEvent.ACTION_DOWN || action == android.view.MotionEvent.ACTION_MOVE || action == android.view.MotionEvent.ACTION_UP) {
                float x = Math.max(0, Math.min(w, event.getX()));
                float fraction = x / w;
                long targetMs = (long) (fraction * controller.getDurationMs());
                progressBar.setProgress((int) (fraction * 1000));
                tvCurrentTime.setText(GlassMediaPlayerController.formatTime(targetMs));
                if (action == android.view.MotionEvent.ACTION_UP) {
                    controller.seekTo(targetMs);
                }
                return true;
            }
            return false;
        });
        timelineLayout.addView(progressBar);

        LinearLayout timeTextRow = new LinearLayout(context);
        timeTextRow.setOrientation(LinearLayout.HORIZONTAL);
        timeTextRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        tvCurrentTime = new TextView(context);
        tvCurrentTime.setText("00:00");
        tvCurrentTime.setTextSize(10f);
        tvCurrentTime.setTextColor(colors.getTextSecondary());
        timeTextRow.addView(tvCurrentTime, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        tvTotalDuration = new TextView(context);
        tvTotalDuration.setText("00:00");
        tvTotalDuration.setTextSize(10f);
        tvTotalDuration.setTextColor(colors.getTextSecondary());
        tvTotalDuration.setGravity(Gravity.END);
        timeTextRow.addView(tvTotalDuration);

        timelineLayout.addView(timeTextRow);
        contentLayout.addView(timelineLayout);

        // Bottom Transport Bar
        transportBar = new LinearLayout(context);
        transportBar.setOrientation(LinearLayout.HORIZONTAL);
        transportBar.setGravity(Gravity.CENTER_VERTICAL);
        int transPadding = GlassDimensions.dpToPx(context, 3);
        transportBar.setPadding(transPadding, transPadding, transPadding, transPadding);
        transportBar.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.PILL_RADIUS_DP, isNight));
        LinearLayout.LayoutParams transLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        transportBar.setLayoutParams(transLp);

        btnFavorite = createTransportButton(context, "♥", colors.getTextSecondary());
        btnFavorite.setOnClickListener(v -> {
            if (controller != null) controller.toggleFavorite();
        });
        transportBar.addView(btnFavorite);

        btnPrev = createTransportButton(context, "⏮", colors.getTextPrimary());
        btnPrev.setOnClickListener(v -> {
            if (controller != null) controller.skipPrevious();
        });
        transportBar.addView(btnPrev);

        // Center Play/Pause Button
        int playTargetDp = me.aap.fermata.BuildConfig.AUTO ? 46 : 42;
        int playBtnSize = GlassDimensions.dpToPx(context, playTargetDp);
        int playColor = isNight ? Color.WHITE : colors.getTextPrimary();
        btnPlayPause = new TextView(context);
        btnPlayPause.setText("▶");
        btnPlayPause.setTextSize(18f);
        btnPlayPause.setTextColor(playColor);
        btnPlayPause.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(playBtnSize, playBtnSize);
        int playMarginH = GlassDimensions.dpToPx(context, 4);
        playLp.leftMargin = playMarginH;
        playLp.rightMargin = playMarginH;
        btnPlayPause.setLayoutParams(playLp);
        btnPlayPause.setBackground(GlassDrawables.createGlassPillDrawable(context, playTargetDp / 2f, isNight));
        btnPlayPause.setOnClickListener(v -> {
            if (controller != null) {
                if (controller.getPresentationState() == GlassMediaPlayerController.PresentationState.IDLE_NO_RECENT) {
                    handleCardClick();
                    return;
                }
                controller.togglePlayPause();
            }
        });
        transportBar.addView(btnPlayPause);

        btnNext = createTransportButton(context, "⏭", colors.getTextPrimary());
        btnNext.setOnClickListener(v -> {
            if (controller != null) controller.skipNext();
        });
        transportBar.addView(btnNext);

        contentLayout.addView(transportBar);
        addView(contentLayout);
    }

    public void refreshAppearance() {
        Context context = getContext();
        this.isNight = GlassThemeColors.isNight(context);
        this.colors = GlassThemeColors.get(context, isNight);

        setBackground(GlassDrawables.createGlassCardDrawable(context, GlassDimensions.CARD_RADIUS_DP, isNight));

        if (tvHeader != null) {
            tvHeader.setTextColor(colors.getTextSecondary());
        }
        if (ivAlbumArt != null) {
            ivAlbumArt.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.ICON_RADIUS_DP, isNight));
        }
        if (tvTrackTitle != null) {
            tvTrackTitle.setTextColor(colors.getTextPrimary());
        }
        if (tvArtist != null) {
            tvArtist.setTextColor(colors.getTextSecondary());
        }
        if (tvCurrentTime != null) {
            tvCurrentTime.setTextColor(colors.getTextSecondary());
        }
        if (tvTotalDuration != null) {
            tvTotalDuration.setTextColor(colors.getTextSecondary());
        }
        if (transportBar != null) {
            transportBar.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.PILL_RADIUS_DP, isNight));
        }
        if (btnFavorite != null) {
            btnFavorite.setTextColor(controller != null && controller.isFavorite() ? Color.RED : colors.getTextSecondary());
        }
        if (btnPrev != null) {
            btnPrev.setTextColor(colors.getTextPrimary());
        }
        if (btnNext != null) {
            btnNext.setTextColor(colors.getTextPrimary());
        }
        if (btnPlayPause != null) {
            int playTargetDp = me.aap.fermata.BuildConfig.AUTO ? 46 : 42;
            int playColor = isNight ? Color.WHITE : colors.getTextPrimary();
            btnPlayPause.setTextColor(playColor);
            btnPlayPause.setBackground(GlassDrawables.createGlassPillDrawable(context, playTargetDp / 2f, isNight));
        }
        if (ivAlbumArt != null) {
            ivAlbumArt.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.ICON_RADIUS_DP, isNight));
        }
        if (controller != null) {
            controller.refresh();
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            float density = getResources().getDisplayMetrics().density;
            float hDp = h / Math.max(0.1f, density);
            float wDp = w / Math.max(0.1f, density);
            if (hDp < 220f || wDp < 160f) {
                tvTrackTitle.setTextSize(14f);
                tvArtist.setTextSize(11f);
            } else if (hDp < 260f || wDp < 190f) {
                tvTrackTitle.setTextSize(15f);
                tvArtist.setTextSize(12f);
            } else {
                tvTrackTitle.setTextSize(16f);
                tvArtist.setTextSize(13f);
            }
        }
    }

    private TextView createTransportButton(Context context, String symbol, int color) {
        TextView tv = new TextView(context);
        tv.setText(symbol);
        tv.setTextSize(17f);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        int targetHeightDp = me.aap.fermata.BuildConfig.AUTO ? 44 : 40;
        int heightPx = GlassDimensions.dpToPx(context, targetHeightDp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, heightPx, 1.0f);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void handleCardClick() {
        if (controller == null) return;
        GlassMediaPlayerController.PresentationState state = controller.getPresentationState();
        if (state == GlassMediaPlayerController.PresentationState.IDLE_NO_RECENT) {
            if (cardClickListener != null) {
                cardClickListener.onBrowseMedia();
            } else if (browseMediaClickListener != null) {
                browseMediaClickListener.onBrowseMediaClick();
            }
            return;
        }
        if (state == GlassMediaPlayerController.PresentationState.IDLE_WITH_RECENT) {
            if (cardClickListener != null) {
                cardClickListener.onResumeRecent();
            }
            return;
        }
        ControlOnlyPresentation controlOnly = controller.getControlOnly();
        if (controlOnly != null) {
            if (cardClickListener != null) {
                cardClickListener.onOpenControlOnly(controlOnly);
            }
            return;
        }
        MediaLib.PlayableItem item = controller.getCurrentItem();
        if (item != null) {
            if (cardClickListener != null) {
                cardClickListener.onOpenPlayingItem(item);
            }
        }
    }

    public void setController(@NonNull GlassMediaPlayerController controller) {
        this.controller = controller;
        controller.setListener(this::updateFromController);
        updateFromController();
    }

    public void updateFromController() {
        if (controller == null) return;

        GlassMediaPlayerController.PresentationState state = controller.getPresentationState();
        ControlOnlyPresentation controlOnly = controller.getControlOnly();

        if (controlOnly != null) {
            tvHeader.setText(safeGetString(R.string.glass_media_now_playing, "NOW PLAYING"));
            tvTrackTitle.setText(controller.getDisplayedTitle());
            tvArtist.setText(controller.getDisplayedArtist());
            timelineLayout.setVisibility(View.GONE);
            btnPrev.setVisibility(controller.canSkipPrevious() ? View.VISIBLE : View.INVISIBLE);
            btnNext.setVisibility(controller.canSkipNext() ? View.VISIBLE : View.INVISIBLE);
            btnFavorite.setVisibility(View.GONE);
            btnPlayPause.setText(controller.isPlaying() ? "⏸" : "▶");
        } else {
            switch (state) {
                case IDLE_NO_RECENT:
                    tvHeader.setText(safeGetString(R.string.glass_media_library, "MEDIA LIBRARY"));
                    tvTrackTitle.setText(safeGetString(R.string.glass_media_browse_library, "Browse Media Library"));
                    tvArtist.setText(safeGetString(R.string.glass_media_choose_hint, "Tap to choose music or video"));
                    ivAlbumArt.setImageResource(R.drawable.audiotrack);
                    ivAlbumArt.setColorFilter(colors.getActionText());
                    timelineLayout.setVisibility(View.GONE);
                    btnPlayPause.setText("📁");
                    btnPrev.setVisibility(View.INVISIBLE);
                    btnNext.setVisibility(View.INVISIBLE);
                    btnFavorite.setVisibility(View.GONE);
                    break;
                case IDLE_WITH_RECENT:
                    tvHeader.setText(safeGetString(R.string.glass_media_resume_recent, "RESUME RECENT"));
                    tvTrackTitle.setText(controller.getDisplayedTitle());
                    tvArtist.setText(controller.getDisplayedArtist());
                    ivAlbumArt.setImageResource(R.drawable.audiotrack);
                    ivAlbumArt.setColorFilter(colors.getActionText());
                    timelineLayout.setVisibility(View.GONE);
                    btnPlayPause.setText("▶");
                    btnPrev.setVisibility(View.INVISIBLE);
                    btnNext.setVisibility(View.INVISIBLE);
                    btnFavorite.setVisibility(View.GONE);
                    break;
                case LIVE:
                    tvHeader.setText(safeGetString(R.string.glass_media_live_stream, "LIVE STREAM"));
                    tvTrackTitle.setText(controller.getDisplayedTitle());
                    tvArtist.setText(controller.getDisplayedArtist());
                    timelineLayout.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.INVISIBLE);
                    tvCurrentTime.setText(safeGetString(R.string.glass_media_live_badge, "LIVE"));
                    tvCurrentTime.setTextColor(Color.RED);
                    tvTotalDuration.setText("");
                    btnPrev.setVisibility(controller.canSkipPrevious() ? View.VISIBLE : View.INVISIBLE);
                    btnNext.setVisibility(controller.canSkipNext() ? View.VISIBLE : View.INVISIBLE);
                    btnFavorite.setVisibility(View.VISIBLE);
                    btnPlayPause.setText(controller.isPlaying() ? "⏸" : "▶");
                    break;
                case BUFFERING:
                    tvHeader.setText(safeGetString(R.string.glass_media_buffering, "BUFFERING..."));
                    tvTrackTitle.setText(controller.getDisplayedTitle());
                    tvArtist.setText(controller.getDisplayedArtist());
                    if (controller.isSeekable()) {
                        timelineLayout.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.VISIBLE);
                    } else {
                        timelineLayout.setVisibility(View.GONE);
                    }
                    tvCurrentTime.setTextColor(colors.getTextSecondary());
                    btnPrev.setVisibility(controller.canSkipPrevious() ? View.VISIBLE : View.INVISIBLE);
                    btnNext.setVisibility(controller.canSkipNext() ? View.VISIBLE : View.INVISIBLE);
                    btnFavorite.setVisibility(View.VISIBLE);
                    btnPlayPause.setText("⏸");
                    break;
                case ERROR:
                    tvHeader.setText(safeGetString(R.string.glass_media_error, "PLAYBACK ERROR"));
                    tvTrackTitle.setText(controller.getDisplayedTitle());
                    tvArtist.setText(safeGetString(R.string.glass_media_retry_hint, "Playback failed. Tap to retry."));
                    timelineLayout.setVisibility(View.GONE);
                    btnPrev.setVisibility(View.INVISIBLE);
                    btnNext.setVisibility(View.INVISIBLE);
                    btnFavorite.setVisibility(View.GONE);
                    btnPlayPause.setText("▶");
                    break;
                case PLAYING:
                case PAUSED:
                default:
                    tvHeader.setText(safeGetString(R.string.glass_media_now_playing, "NOW PLAYING"));
                    tvTrackTitle.setText(controller.getDisplayedTitle());
                    tvArtist.setText(controller.getDisplayedArtist());
                    if (controller.isSeekable()) {
                        timelineLayout.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.VISIBLE);
                    } else {
                        timelineLayout.setVisibility(View.GONE);
                    }
                    btnPrev.setVisibility(controller.canSkipPrevious() ? View.VISIBLE : View.INVISIBLE);
                    btnNext.setVisibility(controller.canSkipNext() ? View.VISIBLE : View.INVISIBLE);
                    btnFavorite.setVisibility(View.VISIBLE);
                    btnPlayPause.setText(controller.isPlaying() ? "⏸" : "▶");
                    tvCurrentTime.setTextColor(colors.getTextSecondary());
                    break;
            }
        }

        Bitmap art = controller.getAlbumArt();
        if (art != null) {
            ivAlbumArt.clearColorFilter();
            ivAlbumArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = GlassDimensions.dpToPx(getContext(), 4);
            ivAlbumArt.setPadding(pad, pad, pad, pad);
            ivAlbumArt.setImageBitmap(art);
        } else if (state != GlassMediaPlayerController.PresentationState.IDLE_NO_RECENT
                && state != GlassMediaPlayerController.PresentationState.IDLE_WITH_RECENT) {
            ivAlbumArt.setColorFilter(colors.getActionText());
            ivAlbumArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = GlassDimensions.dpToPx(getContext(), 10);
            ivAlbumArt.setPadding(pad, pad, pad, pad);
            ivAlbumArt.setImageResource(R.drawable.audiotrack);
        } else {
            ivAlbumArt.setColorFilter(colors.getActionText());
            ivAlbumArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = GlassDimensions.dpToPx(getContext(), 10);
            ivAlbumArt.setPadding(pad, pad, pad, pad);
            ivAlbumArt.setImageResource(R.drawable.audiotrack);
        }

        btnFavorite.setTextColor(controller.isFavorite() ? Color.RED : colors.getTextSecondary());

        if (controlOnly == null && controller.isSeekable()
                && state != GlassMediaPlayerController.PresentationState.LIVE
                && state != GlassMediaPlayerController.PresentationState.IDLE_NO_RECENT
                && state != GlassMediaPlayerController.PresentationState.IDLE_WITH_RECENT
                && state != GlassMediaPlayerController.PresentationState.ERROR) {
            long pos = controller.getPositionMs();
            long dur = controller.getDurationMs();

            tvCurrentTime.setText(GlassMediaPlayerController.formatTime(pos));
            tvTotalDuration.setText(GlassMediaPlayerController.formatTime(dur));

            if (dur > 0) {
                int progress = (int) ((pos * 1000) / dur);
                progressBar.setProgress(Math.max(0, Math.min(1000, progress)));
            } else {
                progressBar.setProgress(0);
            }
        }
    }

    private String safeGetString(int resId, String fallback) {
        try {
            return getContext().getString(resId);
        } catch (Throwable e) {
            return fallback;
        }
    }
}
