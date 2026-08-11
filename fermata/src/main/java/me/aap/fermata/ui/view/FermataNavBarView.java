package me.aap.fermata.ui.view;

import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.widget.ImageViewCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.GestureListener;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.NavButtonView;

/** Shared responsive navigation rail used by phone, mirror, and projection hosts. */
public class FermataNavBarView extends NavBarView implements GestureListener {
	private static final float INACTIVE_ICON_ALPHA = 0.74F;
	private final GestureDetectorCompat gestureDetector;
	private final Runnable refreshScrollStateTask = this::refreshScrollState;
	private int platformTouchSlop;
	private float touchDownX;
	private float touchDownY;
	private View touchTargetChild;
	private boolean suppressClickUntilUp;
	private NavRailLayoutPolicy.GestureAxis gestureAxis =
			NavRailLayoutPolicy.GestureAxis.UNDECIDED;
	private boolean initialized;
	private LinearLayoutCompat fixedZone;
	private NavRailViewport scrollViewport;

	public FermataNavBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		gestureDetector = new GestureDetectorCompat(context, this);
		init();
	}

	public FermataNavBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		gestureDetector = new GestureDetectorCompat(context, this);
		init();
	}

	private void init() {
		platformTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		setClipToPadding(true);
		setVerticalScrollBarEnabled(false);
		initialized = true;
		setBackgroundResource(isRight() ? R.drawable.aa_projected_nav_rail_bg_right :
				R.drawable.aa_projected_nav_rail_bg_left);
		ensureRailStructure();
		setSize(getMainActivity().getPrefs().getNavBarSizePref(getMainActivity()));
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN -> {
				touchDownX = event.getX();
				touchDownY = event.getY();
				touchTargetChild = findTouchedChild(event);
				suppressClickUntilUp = false;
				gestureAxis = NavRailLayoutPolicy.GestureAxis.UNDECIDED;
				gestureDetector.onTouchEvent(event);
			}
			case MotionEvent.ACTION_MOVE -> {
				if (!suppressClickUntilUp) {
					gestureAxis = resolveGestureAxis(event);
					if (gestureAxis != NavRailLayoutPolicy.GestureAxis.UNDECIDED) {
						suppressClickUntilUp = true;
						dispatchCancelToPressedChild(event);
					}
				}
				if (suppressClickUntilUp) {
					gestureDetector.onTouchEvent(event);
					return true;
				}
			}
			case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				if (suppressClickUntilUp) {
					gestureDetector.onTouchEvent(event);
					suppressClickUntilUp = false;
					gestureAxis = NavRailLayoutPolicy.GestureAxis.UNDECIDED;
					touchTargetChild = null;
					return true;
				}
				gestureDetector.onTouchEvent(event);
				gestureAxis = NavRailLayoutPolicy.GestureAxis.UNDECIDED;
				touchTargetChild = null;
			}
		}

		return super.dispatchTouchEvent(event);
	}

	@Override
	protected boolean interceptTouchEvent(MotionEvent event) {
		// ACTION_DOWN is supplied by dispatchTouchEvent(). MOVE must not reach Android's
		// lower default slop before the rail's projection-aware threshold is crossed.
		return super.onTouchEvent(event);
	}

	private NavRailLayoutPolicy.GestureAxis resolveGestureAxis(MotionEvent event) {
		float dx = Math.abs(event.getX() - touchDownX);
		float dy = Math.abs(event.getY() - touchDownY);
		boolean projection = getMainActivity().getRuntimeHostMode().isProjection();
		return NavRailLayoutPolicy.resolveGestureAxis(dx, dy, platformTouchSlop, projection);
	}

	private void dispatchCancelToPressedChild(MotionEvent source) {
		if (touchTargetChild == null) return;
		MotionEvent cancel = MotionEvent.obtain(source);
		cancel.setAction(MotionEvent.ACTION_CANCEL);
		touchTargetChild.dispatchTouchEvent(cancel);
		cancel.recycle();
	}

	@Nullable
	private View findTouchedChild(MotionEvent event) {
		float x = event.getX();
		float y = event.getY();
		for (int i = getChildCount() - 1; i >= 0; i--) {
			View child = getChildAt(i);
			if ((child.getVisibility() == VISIBLE) && (x >= child.getLeft()) &&
					(x < child.getRight()) && (y >= child.getTop()) && (y < child.getBottom())) {
				return child;
			}
		}
		return null;
	}

	@Override
	protected MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	@Override
	public void addView(View child) {
		if (!initialized) {
			super.addView(child);
			return;
		}
		addNavigationItem(child);
	}

	@Override
	public void removeAllViews() {
		if (!initialized || (fixedZone == null)) {
			super.removeAllViews();
			return;
		}
		fixedZone.removeAllViews();
		scrollViewport.clearNavigationItems();
	}

	@Override
	public void setSize(float scale) {
		if (!initialized) {
			super.setSize(scale);
			return;
		}
		var config = getResources().getConfiguration();
		boolean projection = getMainActivity().getRuntimeHostMode().isProjection();
		int widthDp = NavRailLayoutPolicy.railWidthDp(projection, config.screenWidthDp, scale);
		var lp = getLayoutParams();
		if (lp == null) lp = new LinearLayoutCompat.LayoutParams(0, 0);
		lp.width = UiUtils.toIntPx(getContext(), widthDp);
		lp.height = LinearLayoutCompat.LayoutParams.MATCH_PARENT;
		setLayoutParams(lp);
		requestLayout();
	}

	@Override
	protected boolean setMediator(ActivityFragment fragment) {
		boolean attached = super.setMediator(fragment);
		// NavBarView restores its legacy raw width when scale == 1. Re-apply the shared
		// responsive width after every mediator attachment, including Mobile reloads.
		if (initialized) setSize(getMainActivity().getPrefs().getNavBarSizePref(getMainActivity()));
		return attached;
	}

	public void forEachNavigationItem(Consumer<View> consumer) {
		if (!initialized || (fixedZone == null)) {
			for (int i = 0, count = getChildCount(); i < count; i++) consumer.accept(getChildAt(i));
			return;
		}
		for (int i = 0, count = fixedZone.getChildCount(); i < count; i++) {
			consumer.accept(fixedZone.getChildAt(i));
		}
		scrollViewport.forEachNavigationItem(consumer);
	}

	public void setVoiceVisible(boolean visible) {
		View voice = findViewById(R.id.nav_voice);
		if (voice != null) voice.setVisibility(visible ? VISIBLE : GONE);
		post(refreshScrollStateTask);
	}

	private void ensureRailStructure() {
		if (fixedZone != null) return;
		List<View> earlyChildren = new ArrayList<>(getChildCount());
		for (int i = 0, count = getChildCount(); i < count; i++) earlyChildren.add(getChildAt(i));
		super.removeAllViews();

		fixedZone = new LinearLayoutCompat(getContext());
		fixedZone.setOrientation(VERTICAL);
		fixedZone.setClipChildren(false);
		View divider = new View(getContext());
		divider.setBackgroundResource(R.drawable.aa_nav_divider);
		scrollViewport = new NavRailViewport(getContext(), getTint(), isRight());

		super.addView(fixedZone, new LinearLayoutCompat.LayoutParams(
				LinearLayoutCompat.LayoutParams.MATCH_PARENT,
				LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
		int dividerHeight = UiUtils.toIntPx(getContext(), NavRailLayoutPolicy.separatorExtentDp());
		var dividerLp = new LinearLayoutCompat.LayoutParams(
				LinearLayoutCompat.LayoutParams.MATCH_PARENT, dividerHeight);
		int horizontal = UiUtils.toIntPx(getContext(), 16);
		dividerLp.setMargins(horizontal, 0, horizontal, 0);
		super.addView(divider, dividerLp);
		super.addView(scrollViewport, new LinearLayoutCompat.LayoutParams(
				LinearLayoutCompat.LayoutParams.MATCH_PARENT, 0, 1F));

		for (View child : earlyChildren) addNavigationItem(child);
	}

	private void addNavigationItem(View child) {
		ensureRailStructure();
		int id = child.getId();
		if ((id == R.id.dashboard_fragment) || (id == R.id.nav_voice)) fixedZone.addView(child);
		else scrollViewport.addNavigationItem(child);
		child.setOnFocusChangeListener(this::onNavigationItemFocusChanged);
		sizeVerticalNavButton(child, getVerticalButtonExtent());
		applyVisualState(child);
		post(refreshScrollStateTask);
	}

	private void onNavigationItemFocusChanged(View view, boolean focused) {
		applyVisualState(view);
		if (focused && scrollViewport.contains(view)) {
			post(() -> scrollViewport.ensureVisible(view, true));
		}
	}

	@Override
	protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
		super.onSizeChanged(width, height, oldWidth, oldHeight);
		sizeVerticalNavButtons(height);
		post(refreshScrollStateTask);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		sizeVerticalNavButtons(MeasureSpec.getSize(heightMeasureSpec));
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		sizeVerticalNavButtons(bottom - top);
		super.onLayout(changed, left, top, right, bottom);
		refreshScrollState();
	}

	@Override
	public void onActivityEvent(ActivityDelegate activity, long event) {
		super.onActivityEvent(activity, event);
		if ((event == FRAGMENT_CHANGED) || (event == FRAGMENT_CONTENT_CHANGED)) {
			post(refreshScrollStateTask);
		}
	}

	@Override
	public boolean onSwipeLeft(MotionEvent first, MotionEvent second) {
		return (gestureAxis == NavRailLayoutPolicy.GestureAxis.HORIZONTAL) &&
				getMainActivity().getControlPanel().onSwipeLeft(first, second);
	}

	@Override
	public boolean onSwipeRight(MotionEvent first, MotionEvent second) {
		return (gestureAxis == NavRailLayoutPolicy.GestureAxis.HORIZONTAL) &&
				getMainActivity().getControlPanel().onSwipeRight(first, second);
	}

	@Override
	public boolean onScroll(MotionEvent first, MotionEvent second,
			float distanceX, float distanceY) {
		if (gestureAxis == NavRailLayoutPolicy.GestureAxis.VERTICAL) {
			scrollViewport.scrollByDistance(distanceY);
			return true;
		}
		if (gestureAxis == NavRailLayoutPolicy.GestureAxis.HORIZONTAL) {
			return getMainActivity().getControlPanel().onScroll(first, second, distanceX, 0F);
		}
		return false;
	}

	@Override
	protected void onDetachedFromWindow() {
		removeCallbacks(refreshScrollStateTask);
		super.onDetachedFromWindow();
	}

	private void refreshScrollState() {
		if (!isAttachedToWindow()) return;
		refreshFocusOrder();
		forEachNavigationItem(this::applyVisualState);
		ensureActiveItemVisible();
		scrollViewport.refreshScrollState();
	}

	private void ensureActiveItemVisible() {
		if (getHeight() <= 0) return;
		View active = findViewById(getMainActivity().getActiveNavItemId());
		if (active != null) scrollViewport.ensureVisible(active, false);
	}

	private void refreshFocusOrder() {
		List<View> visible = new ArrayList<>();
		forEachNavigationItem(item -> {
			if ((item.getVisibility() == VISIBLE) && item.isFocusable() && (item.getId() != NO_ID)) {
				visible.add(item);
			}
		});
		int count = visible.size();
		for (int i = 0; i < count; i++) {
			View item = visible.get(i);
			item.setNextFocusUpId(visible.get((i + count - 1) % count).getId());
			item.setNextFocusDownId(visible.get((i + 1) % count).getId());
		}
	}

	private void applyVisualState(View item) {
		if (!(item instanceof NavButtonView button)) return;
		button.getIcon().setAlpha(item.isSelected() || item.isFocused() ?
				1F : INACTIVE_ICON_ALPHA);
	}

	private int getVerticalButtonExtent() {
		return getVerticalButtonExtent(getHeight());
	}

	private void sizeVerticalNavButtons(int height) {
		int buttonExtent = getVerticalButtonExtent(height);
		if (buttonExtent <= 0) return;
		forEachNavigationItem(child -> sizeVerticalNavButton(child, buttonExtent));
	}

	private int getVerticalButtonExtent(int navBarHeight) {
		float density = getResources().getDisplayMetrics().density;
		int heightDp = (navBarHeight > 0) ? Math.round(navBarHeight / density) :
				getResources().getConfiguration().screenHeightDp;
		boolean projection = getMainActivity().getRuntimeHostMode().isProjection();
		return UiUtils.toIntPx(getContext(),
				NavRailLayoutPolicy.touchTargetExtentDp(projection, heightDp));
	}

	private void sizeVerticalNavButton(View child, int extent) {
		if (!(child instanceof NavButtonView button) || (extent <= 0)) return;
		var lp = child.getLayoutParams();
		if (lp == null) return;
		boolean changed = (lp.width != extent) || (lp.height != extent);
		lp.width = extent;
		lp.height = extent;

		if (lp instanceof LinearLayoutCompat.LayoutParams layout) {
			if (layout.weight != 0F) changed = true;
			layout.weight = 0F;
			layout.gravity = Gravity.CENTER_HORIZONTAL;
			if ((layout.topMargin != 0) || (layout.bottomMargin != 0)) changed = true;
			layout.setMargins(0, 0, 0, 0);
		}

		configureIcon(button);
		button.setBackgroundResource((child.getId() == R.id.nav_voice) ?
				R.drawable.aa_nav_voice_bg : isRight() ?
				R.drawable.aa_projected_nav_button_bg_right :
				R.drawable.aa_projected_nav_button_bg_left);
		if (changed) child.setLayoutParams(lp);
	}

	private void configureIcon(NavButtonView button) {
		int iconExtent = UiUtils.toIntPx(getContext(), NavRailLayoutPolicy.iconExtentDp());
		var icon = button.getIcon();
		var iconLp = icon.getLayoutParams();
		if (iconLp instanceof LinearLayoutCompat.LayoutParams layout) {
			layout.width = iconExtent;
			layout.height = iconExtent;
			layout.weight = 0F;
			layout.gravity = Gravity.CENTER;
			icon.setLayoutParams(layout);
		}
		button.setGravity(Gravity.CENTER);
		button.setIconPadding(0);
		icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
		ImageViewCompat.setImageTintList(icon,
				AppCompatResources.getColorStateList(getContext(), R.color.aa_nav_icon_tint));
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}
}
