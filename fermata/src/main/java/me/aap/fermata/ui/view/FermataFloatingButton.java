package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.view.FloatingButton;

/**
 * Fermata floating action button.
 *
 * <p>PHONE uses the toolbar for Back. The floating button remains available only for root-page
 * menu/add actions and must never reappear as a round Back overlay on nested or video pages.
 * Automotive hosts keep their existing presentation policy.</p>
 */
public class FermataFloatingButton extends FloatingButton {
	public FermataFloatingButton(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void setVisibility(int visibility) {
		super.setVisibility((visibility == VISIBLE) && suppressPhoneBackOverlay() ? GONE : visibility);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		if (suppressPhoneBackOverlay()) super.setVisibility(GONE);
	}

	private boolean suppressPhoneBackOverlay() {
		try {
			MainActivityDelegate activity = MainActivityDelegate.get(getContext());
			return !activity.getRuntimeHostMode().usesAutomotivePresentation() &&
					(activity.isVideoMode() || !activity.isRootPage());
		} catch (RuntimeException ignored) {
			return false;
		}
	}
}
