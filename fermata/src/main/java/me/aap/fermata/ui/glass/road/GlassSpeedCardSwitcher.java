package me.aap.fermata.ui.glass.road;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.function.Consumer;

import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.glass.speedo.GlassSpeedoTripView;
import me.aap.utils.pref.PreferenceStore;

public class GlassSpeedCardSwitcher extends FrameLayout {

    private final GlassSpeedoTripView speedView;
    private final GlassLimitsSignalsView limitsView;
    private final MainActivityPrefs prefs;
    private final Consumer<Boolean> onLimitsVisible;
    // The store keeps listeners only weakly, so this field keeps it alive as long as the switcher.
    private final PreferenceStore.Listener prefsListener;
    private boolean isLimits;

    public GlassSpeedCardSwitcher(@NonNull Context context,
                                  @NonNull GlassSpeedoTripView speed,
                                  @NonNull GlassLimitsSignalsView limits,
                                  @NonNull MainActivityPrefs prefs,
                                  @NonNull Consumer<Boolean> onLimitsVisible) {
        super(context);
        this.speedView = speed;
        this.limitsView = limits;
        this.prefs = prefs;
        this.onLimitsVisible = onLimitsVisible;

        addView(speed, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(limits, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        speed.setOnSwapListener(this::swap);
        limits.setOnSwapListener(this::swap);

        boolean roadDataEnabled = prefs.getBooleanPref(MainActivityPrefs.GLASS_ROAD_DATA);
        if (!roadDataEnabled) {
            isLimits = false;
            speed.setVisibility(View.VISIBLE);
            speed.setAlpha(1f);
            limits.setVisibility(View.GONE);
            speed.setSwapVisible(false);
            onLimitsVisible.accept(false);
        } else {
            speed.setSwapVisible(true);
            int savedCard = prefs.getIntPref(MainActivityPrefs.GLASS_SPEED_CARD);
            if (savedCard == 1) {
                isLimits = true;
                limits.setVisibility(View.VISIBLE);
                limits.setAlpha(1f);
                speed.setVisibility(View.GONE);
                onLimitsVisible.accept(true);
            } else {
                isLimits = false;
                speed.setVisibility(View.VISIBLE);
                speed.setAlpha(1f);
                limits.setVisibility(View.GONE);
                onLimitsVisible.accept(false);
            }
        }

        prefsListener = (store, p) -> {
            if (p.contains(MainActivityPrefs.GLASS_ROAD_DATA)) {
                boolean enabled = store.getBooleanPref(MainActivityPrefs.GLASS_ROAD_DATA);
                speed.setSwapVisible(enabled);
                if (!enabled && isLimits) {
                    forceSpeedCard();
                }
            }
        };
        prefs.addBroadcastListener(prefsListener);
    }

    public boolean isLimitsShown() {
        return isLimits;
    }

    public void swap() {
        boolean roadDataEnabled = prefs.getBooleanPref(MainActivityPrefs.GLASS_ROAD_DATA);
        if (!roadDataEnabled) {
            forceSpeedCard();
            return;
        }

        if (isLimits) {
            isLimits = false;
            prefs.applyIntPref(MainActivityPrefs.GLASS_SPEED_CARD, 0);
            speedView.setAlpha(0f);
            speedView.setVisibility(View.VISIBLE);
            speedView.animate().alpha(1f).setDuration(150).start();
            limitsView.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                if (!isLimits) limitsView.setVisibility(View.GONE);
            }).start();
            onLimitsVisible.accept(false);
        } else {
            isLimits = true;
            prefs.applyIntPref(MainActivityPrefs.GLASS_SPEED_CARD, 1);
            limitsView.setAlpha(0f);
            limitsView.setVisibility(View.VISIBLE);
            limitsView.animate().alpha(1f).setDuration(150).start();
            speedView.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                if (isLimits) speedView.setVisibility(View.GONE);
            }).start();
            onLimitsVisible.accept(true);
        }
    }

    private void forceSpeedCard() {
        isLimits = false;
        prefs.applyIntPref(MainActivityPrefs.GLASS_SPEED_CARD, 0);
        speedView.setVisibility(View.VISIBLE);
        speedView.setAlpha(1f);
        limitsView.setVisibility(View.GONE);
        speedView.setSwapVisible(false);
        onLimitsVisible.accept(false);
    }

    public void refreshAppearance() {
        speedView.refreshAppearance();
        limitsView.refreshAppearance();
    }
}
