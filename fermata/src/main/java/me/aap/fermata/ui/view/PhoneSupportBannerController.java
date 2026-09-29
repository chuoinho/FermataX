package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.policy.PhoneRootPolicy;
import me.aap.fermata.util.Utils;
import me.aap.utils.ui.UiUtils;

/**
 * Transient, non-intrusive floating support banner shown on phone launch.
 * Displays for 4 seconds inviting support/donation, then smoothly auto-dismisses.
 * Has zero impact on user experience: wrap_content touch boundaries mean touches everywhere
 * else pass directly to underlying content without obstruction.
 */
public final class PhoneSupportBannerController implements MainActivityListener {
	private static final String TAG = "PhoneSupportBanner";
	public static final String DONATE_URL = "https://ko-fi.com/fermatax";
	public static final long DISPLAY_DURATION_MS = 4000L;
	public static final long INITIAL_DELAY_MS = 600L;
	public static final long MIN_RESUME_INTERVAL_MS = 10000L;

	private final MainActivityDelegate activity;
	private final View bannerView;
	private final Runnable dismissRunnable = this::dismissAnimated;
	private boolean dismissed = false;
	private static long lastShowTime = 0L;

	public PhoneSupportBannerController(MainActivityDelegate activity, View bannerView) {
		this.activity = activity;
		this.bannerView = bannerView;
		if (bannerView == null) return;

		bannerView.setVisibility(GONE);
		bannerView.setOnClickListener(v -> {
			dismissInstant();
			Utils.openUrl(activity.getContext(), DONATE_URL);
		});

		View closeBtn = bannerView.findViewById(R.id.phone_support_close);
		if (closeBtn != null) {
			closeBtn.setOnClickListener(v -> dismissAnimated());
		}

		activity.addBroadcastListener(this);
	}

	public void start() {
		if (bannerView == null) return;
		if (!PhoneRootPolicy.usesPhoneRoots(activity.getRuntimeHostMode())) return;
		if (activity.isVideoMode()) return;
		if (activity.getAppActivity().isDestroyed() || activity.getAppActivity().isFinishing()) return;

		long now = System.currentTimeMillis();
		if (now - lastShowTime < MIN_RESUME_INTERVAL_MS) {
			Log.d(TAG, "Suppressed by interval check: " + (now - lastShowTime) + "ms");
			return;
		}
		lastShowTime = now;
		dismissed = false;

		bannerView.removeCallbacks(dismissRunnable);
		bannerView.postDelayed(this::showAnimated, INITIAL_DELAY_MS);
		Log.d(TAG, "Banner scheduled to show in " + INITIAL_DELAY_MS + "ms");
	}

	public void showAnimated() {
		if (bannerView == null || dismissed) return;
		if (activity.getAppActivity().isDestroyed() || activity.getAppActivity().isFinishing() || activity.isVideoMode()) return;

		float offset = UiUtils.toPx(bannerView.getContext(), 24);
		bannerView.setTranslationY(offset);
		bannerView.setAlpha(0f);
		bannerView.setVisibility(VISIBLE);

		bannerView.animate()
				.translationY(0f)
				.alpha(1f)
				.setDuration(280L)
				.setInterpolator(new DecelerateInterpolator())
				.setListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						if (!dismissed && bannerView != null) {
							bannerView.postDelayed(dismissRunnable, DISPLAY_DURATION_MS);
						}
					}
				})
				.start();
		Log.d(TAG, "Banner show animation started");
	}

	public void dismissAnimated() {
		if (dismissed || bannerView == null) return;
		dismissed = true;
		bannerView.removeCallbacks(dismissRunnable);

		if (bannerView.getVisibility() != VISIBLE) {
			bannerView.setVisibility(GONE);
			return;
		}

		float offset = UiUtils.toPx(bannerView.getContext(), 24);
		bannerView.animate()
				.translationY(offset)
				.alpha(0f)
				.setDuration(280L)
				.setInterpolator(new AccelerateInterpolator())
				.setListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						if (bannerView != null) {
							bannerView.setVisibility(GONE);
						}
					}
				})
				.start();
		Log.d(TAG, "Banner dismiss animation started");
	}

	public void dismissInstant() {
		if (dismissed || bannerView == null) return;
		dismissed = true;
		bannerView.removeCallbacks(dismissRunnable);
		bannerView.animate().cancel();
		bannerView.setVisibility(GONE);
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) {
			dismissInstant();
		} else if (e == MODE_CHANGED || e == FRAGMENT_CHANGED) {
			if (bannerView != null && bannerView.getVisibility() == VISIBLE) {
				dismissAnimated();
			}
		}
	}

	public static void resetShowTimeForTesting() {
		lastShowTime = 0L;
	}
}
