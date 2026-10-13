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
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;

/**
 * Frosted glass automotive media player card with album art, scrub timeline, and transport controls.
 */
public class GlassMediaPlayerView extends FrameLayout {

    private final boolean isNight;
    private final GlassThemeColors colors;

    private TextView tvHeader;
    private ImageView ivAlbumArt;
    private TextView tvTrackTitle;
    private TextView tvArtist;
    private LinearLayout timelineLayout;
    private TextView tvCurrentTime;
    private TextView tvTotalDuration;
    private ProgressBar progressBar;

    private TextView btnPrev;
    private TextView btnPlayPause;
    private TextView btnNext;
    private TextView btnFavorite;

    private GlassMediaPlayerController controller;

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
        int padding = GlassDimensions.dpToPx(context, 16);
        setPadding(padding, padding, padding, padding);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        contentLayout.setLayoutParams(lp);

        // Header
        tvHeader = new TextView(context);
        tvHeader.setText("NOW PLAYING");
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
        int artSize = GlassDimensions.dpToPx(context, 72);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(artSize, artSize);
        artLp.rightMargin = GlassDimensions.dpToPx(context, 14);
        ivAlbumArt.setLayoutParams(artLp);
        ivAlbumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivAlbumArt.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.ICON_RADIUS_DP, isNight));
        centerLayout.addView(ivAlbumArt);

        // Track & Artist text
        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        textLayout.setLayoutParams(textLp);

        tvTrackTitle = new TextView(context);
        tvTrackTitle.setText("No Media Playing");
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
        contentLayout.addView(centerLayout);

        // Timeline Progress Bar
        timelineLayout = new LinearLayout(context);
        timelineLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlLp.bottomMargin = GlassDimensions.dpToPx(context, 8);
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
        LinearLayout transportBar = new LinearLayout(context);
        transportBar.setOrientation(LinearLayout.HORIZONTAL);
        transportBar.setGravity(Gravity.CENTER);
        int transPadding = GlassDimensions.dpToPx(context, 4);
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

        // Big Play/Pause Center Button
        int playTargetDp = me.aap.fermata.BuildConfig.AUTO ? 76 : 52;
        int playBtnSize = GlassDimensions.dpToPx(context, playTargetDp);
        btnPlayPause = createTransportButton(context, "▶", Color.WHITE);
        btnPlayPause.setLayoutParams(new LinearLayout.LayoutParams(playBtnSize, playBtnSize));
        btnPlayPause.setBackground(GlassDrawables.createGlassPillDrawable(context, playTargetDp / 2f, isNight));
        btnPlayPause.setOnClickListener(v -> {
            if (controller != null) controller.togglePlayPause();
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

    private TextView createTransportButton(Context context, String symbol, int color) {
        TextView tv = new TextView(context);
        tv.setText(symbol);
        tv.setTextSize(18f);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        int targetDp = me.aap.fermata.BuildConfig.AUTO ? 76 : 48;
        int size = GlassDimensions.dpToPx(context, targetDp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        tv.setLayoutParams(lp);
        return tv;
    }

    public void setController(@NonNull GlassMediaPlayerController controller) {
        this.controller = controller;
        controller.setListener(this::updateFromController);
        updateFromController();
    }

    public void updateFromController() {
        if (controller == null) return;

        GlassMediaPlayerController.PresentationState state = controller.getPresentationState();
        switch (state) {
            case IDLE_NO_RECENT:
                tvHeader.setText("MEDIA LIBRARY");
                tvTrackTitle.setText("Tap to browse media");
                tvArtist.setText("Audio, Video, Addons");
                ivAlbumArt.setImageResource(R.drawable.audiotrack);
                timelineLayout.setVisibility(View.GONE);
                btnPlayPause.setText("▶");
                btnFavorite.setVisibility(View.GONE);
                break;
            case IDLE_WITH_RECENT:
                tvHeader.setText("RECENT MEDIA");
                tvTrackTitle.setText(controller.getDisplayedTitle());
                tvArtist.setText(controller.getDisplayedArtist());
                ivAlbumArt.setImageResource(R.drawable.audiotrack);
                timelineLayout.setVisibility(View.GONE);
                btnPlayPause.setText("▶");
                btnFavorite.setVisibility(View.GONE);
                break;
            case LIVE:
                tvHeader.setText("LIVE STREAM");
                tvTrackTitle.setText(controller.getDisplayedTitle());
                tvArtist.setText(controller.getDisplayedArtist());
                timelineLayout.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.INVISIBLE);
                tvCurrentTime.setText("LIVE");
                tvCurrentTime.setTextColor(Color.RED);
                tvTotalDuration.setText("");
                btnFavorite.setVisibility(View.VISIBLE);
                btnPlayPause.setText(controller.isPlaying() ? "⏸" : "▶");
                break;
            case BUFFERING:
                tvHeader.setText("BUFFERING...");
                tvTrackTitle.setText(controller.getDisplayedTitle());
                tvArtist.setText(controller.getDisplayedArtist());
                timelineLayout.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                tvCurrentTime.setTextColor(colors.getTextSecondary());
                btnFavorite.setVisibility(View.VISIBLE);
                btnPlayPause.setText("⏸");
                break;
            case ERROR:
                tvHeader.setText("PLAYBACK ERROR");
                tvTrackTitle.setText(controller.getDisplayedTitle());
                tvArtist.setText("Playback failed. Tap to retry.");
                timelineLayout.setVisibility(View.GONE);
                btnFavorite.setVisibility(View.GONE);
                btnPlayPause.setText("▶");
                break;
            case PLAYING:
            case PAUSED:
            default:
                tvHeader.setText("NOW PLAYING");
                tvTrackTitle.setText(controller.getDisplayedTitle());
                tvArtist.setText(controller.getDisplayedArtist());
                timelineLayout.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                btnFavorite.setVisibility(View.VISIBLE);
                btnPlayPause.setText(controller.isPlaying() ? "⏸" : "▶");
                tvCurrentTime.setTextColor(colors.getTextSecondary());
                break;
        }

        Bitmap art = controller.getAlbumArt();
        if (art != null) {
            ivAlbumArt.setImageBitmap(art);
        } else if (state != GlassMediaPlayerController.PresentationState.IDLE_NO_RECENT
                && state != GlassMediaPlayerController.PresentationState.IDLE_WITH_RECENT) {
            ivAlbumArt.setImageResource(R.drawable.audiotrack);
        }

        btnFavorite.setTextColor(controller.isFavorite() ? Color.RED : colors.getTextSecondary());

        if (state != GlassMediaPlayerController.PresentationState.LIVE
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
}
