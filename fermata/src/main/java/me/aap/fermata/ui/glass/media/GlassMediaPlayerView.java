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

import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;

/**
 * Frosted glass automotive media player card with album art, scrub timeline, and transport controls.
 */
public class GlassMediaPlayerView extends FrameLayout {

    private final boolean isNight;
    private final GlassThemeColors colors;

    private ImageView ivAlbumArt;
    private TextView tvTrackTitle;
    private TextView tvArtist;
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
        int uiMode = getResources().getConfiguration().uiMode;
        this.isNight = (uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                != android.content.res.Configuration.UI_MODE_NIGHT_NO;
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
        TextView tvHeader = new TextView(context);
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
        tvTrackTitle.setMaxLines(1);
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
        LinearLayout timelineLayout = new LinearLayout(context);
        timelineLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlLp.bottomMargin = GlassDimensions.dpToPx(context, 8);
        timelineLayout.setLayoutParams(tlLp);

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgress(0);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, GlassDimensions.dpToPx(context, 6)));
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
        int transPadding = GlassDimensions.dpToPx(context, 6);
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
        btnPlayPause = createTransportButton(context, "▶", Color.WHITE);
        int playBtnSize = GlassDimensions.dpToPx(context, 44);
        btnPlayPause.setLayoutParams(new LinearLayout.LayoutParams(playBtnSize, playBtnSize));
        btnPlayPause.setBackground(GlassDrawables.createGlassPillDrawable(context, 22f, isNight));
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
        int size = GlassDimensions.dpToPx(context, 40);
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

        tvTrackTitle.setText(controller.getDisplayedTitle());
        tvArtist.setText(controller.getDisplayedArtist());

        Bitmap art = controller.getAlbumArt();
        if (art != null) {
            ivAlbumArt.setImageBitmap(art);
        } else {
            ivAlbumArt.setImageDrawable(null);
        }

        btnPlayPause.setText(controller.isPlaying() ? "⏸" : "▶");
        btnFavorite.setTextColor(controller.isFavorite() ? Color.RED : colors.getTextSecondary());

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
