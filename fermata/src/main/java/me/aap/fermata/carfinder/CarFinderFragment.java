package me.aap.fermata.carfinder;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.policy.PhoneRootPolicy;

/**
 * Main Phone UI Fragment for the Car Finder feature.
 * Features FermataX dark navy & electric blue theme:
 * - Always-visible Map Tile View with MapTiler dark tiles & street-level detail (Zoom 19)
 * - High-precision Radar mode with electric blue scanning rings & directional needle
 * - Material 3 dark surface bottom sheet with high-contrast typography
 * - Parking spot photo capture, thumbnail preview, and full-screen view
 * - Parking notes (level, pillar, section)
 * - In-page settings and instant cleanup upon finding the car
 */
public class CarFinderFragment extends MainActivityFragment
		implements CarLocatorManager.UserLocationListener, CompassSensorManager.HeadingListener, CarFinderPrefs.Listener,
		me.aap.fermata.ui.activity.MainActivityListener {

	private static final int ACCENT_BLUE = 0xFF3B82F6;
	private static final int SURFACE_DARK = 0xFF0F141F;
	private static final int CARD_SURFACE = 0xFF131926;
	private static final int CARD_BORDER = 0xFF263552;
	private static final int CARD_BUTTON_BG = 0xFF1A2438;
	private static final int CARD_BUTTON_BORDER = 0xFF2E4065;
	private static final int TEXT_MUTED = 0xFF94A3B8;

	private FrameLayout rootContainer;
	private LinearLayout overviewContainer;
	private LinearLayout radarContainer;

	private CarMapTileView mapTileView;
	private CarRadarView radarView;
	private me.aap.fermata.ui.glass.carfinder.GlassRadarView glassRadarView;

	// Overview mode views
	private LinearLayout overviewHeader;
	private TextView textHeaderTitle;
	private ImageView btnSettings;
	private LinearLayout cardContainer;
	private TextView textCarTitle;
	private TextView textConfidenceBadge;
	private TextView textBasementHint;
	private TextView textCarAddress;
	private TextView textDistance;
	private TextView textWalkingTime;
	private TextView btnRadar;
	private ImageView btnDirections;
	private TextView btnPinManual;
	private LinearLayout btnEmptyPhoto;
	private View cardFoundCar;
	private View cardEmpty;
	private View cardActiveInfo;
	private TextView textEmptyStatus;

	// Photo & Note views
	private LinearLayout photoNoteSection;
	private ImageView imgPhotoThumb;
	private LinearLayout btnTakePhoto;
	private LinearLayout layoutPhotoActions;
	private TextView textNote;

	// Radar mode views
	private ImageView overviewBackButton;
	private LinearLayout radarHeader;
	private LinearLayout radarCenterPill;
	private TextView radarPillText;
	private ImageView radarBackButton;
	private TextView textRadarSubHeader;
	private TextView textRadarDistance;
	private TextView textRadarUnit;
	private TextView textRadarDirection;
	private TextView textRadarAccuracy;
	private TextView btnViewOnMap;
	private TextView btnRadarFound;

	private CompassSensorManager compassManager;
	private CarParkingLocation currentParkingLocation;
	private Location currentUserLocation;
	private boolean isRadarMode = false;
	private float currentAzimuth = 0.0f;

	public boolean isGlassTheme() {
		MainActivityDelegate a = getActivityDelegate();
		return a != null && a.getPrefs().isGlassTheme(a.isCarActivity());
	}

	@Override
	public int getFragmentId() {
		return R.id.car_finder_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return getString(R.string.car_finder_title);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		float density = getResources().getDisplayMetrics().density;
		rootContainer = new FrameLayout(requireContext());
		rootContainer.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));

		setupOverviewModeLayout(density);
		rootContainer.addView(overviewContainer, new FrameLayout.LayoutParams(-1, -1));

		setupRadarModeLayout(density);
		rootContainer.addView(radarContainer, new FrameLayout.LayoutParams(-1, -1));

		compassManager = new CompassSensorManager(requireContext(), this);

		return rootContainer;
	}

	@Override
	public void onStart() {
		super.onStart();
		MainActivityDelegate a = getActivityDelegate();
		if (a != null) {
			a.addBroadcastListener(this, me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED);
		}
		requestLocationPermissionsIfNeeded();
		CarFinderPrefs.registerListener(requireContext(), this);
		currentParkingLocation = CarFinderPrefs.getParkingLocation(requireContext());
		currentUserLocation = CarLocatorManager.get().getCurrentUserLocation();
		if (currentUserLocation == null) {
			currentUserLocation = CarLocatorManager.get().queryBestKnownUserLocation(requireContext());
		}
		CarLocatorManager.get().startTrackingUserLocation(requireContext(), this);
		if (compassManager != null && isRadarMode) {
			compassManager.start();
		}
		applyAppearanceTheme();
		renderParkingStateToViews();
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (e == me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED) {
			applyAppearanceTheme();
			renderParkingStateToViews();
		}
	}

	public void applyAppearanceTheme() {
		Context ctx = getContext();
		if (ctx == null || rootContainer == null) return;
		boolean isGlass = isGlassTheme();
		boolean isNight = (getActivityDelegate() != null) ? getActivityDelegate().getPrefs().isGlassNightMode()
				: me.aap.fermata.ui.glass.theme.GlassThemeColors.isNight(ctx);
		me.aap.fermata.ui.glass.theme.GlassThemeColors gc =
				me.aap.fermata.ui.glass.theme.GlassThemeColors.get(ctx, isNight);

		int bgCol = isGlass ? (isNight ? 0xFF07090D : 0xFFF8FAFC) : 0xFF07090D;
		if (overviewContainer != null) overviewContainer.setBackgroundColor(bgCol);
		if (radarContainer != null) radarContainer.setBackgroundColor(bgCol);

		if (mapTileView != null) {
			mapTileView.setNightMode(isNight);
		}
		if (glassRadarView != null) {
			glassRadarView.setNightMode(isNight);
		}

		if (overviewHeader != null && isGlass) {
			overviewHeader.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassCardDrawable(ctx, 0f, isNight));
		}
		if (overviewBackButton != null && isGlass) {
			overviewBackButton.setColorFilter(isNight ? 0xFFFFFFFF : 0xFF15181A);
			overviewBackButton.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassPillDrawable(ctx, 20f, isNight));
		}
		if (radarHeader != null && isGlass) {
			radarHeader.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassCardDrawable(ctx, 0f, isNight));
		}
		if (textHeaderTitle != null) {
			textHeaderTitle.setTextColor(isGlass ? gc.getTextPrimary() : 0xFFFFFFFF);
		}
		if (btnSettings != null) {
			btnSettings.setColorFilter(isGlass ? gc.getTextPrimary() : 0xFFC3CDDE);
		}
		if (cardContainer != null) {
			if (isGlass) {
				cardContainer.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassCardDrawable(ctx, 24f, isNight));
			} else {
				float density = getResources().getDisplayMetrics().density;
				GradientDrawable sheetBg = new GradientDrawable();
				sheetBg.setColor(SURFACE_DARK);
				float cornerR = 24f * density;
				sheetBg.setCornerRadii(new float[]{cornerR, cornerR, cornerR, cornerR, 0f, 0f, 0f, 0f});
				sheetBg.setStroke(Math.round(1 * density), CARD_BORDER);
				cardContainer.setBackground(sheetBg);
			}
		}
		if (textEmptyStatus != null) {
			textEmptyStatus.setTextColor(isGlass ? gc.getTextSecondary() : TEXT_MUTED);
		}
		if (btnPinManual != null && isGlass) {
			btnPinManual.setTextColor(isNight ? 0xFF00E5FF : 0xFF007A87);
			float density = getResources().getDisplayMetrics().density;
			GradientDrawable gd = new GradientDrawable();
			gd.setColor(isNight ? 0x2500E5FF : 0x1800E5FF);
			gd.setCornerRadius(21f * density);
			gd.setStroke(Math.round(1.5f * density), isNight ? 0xFF00E5FF : 0xFF007A87);
			btnPinManual.setBackground(gd);
		}
		if (textCarTitle != null) {
			textCarTitle.setTextColor(isGlass ? gc.getTextPrimary() : 0xFFFFFFFF);
		}
		if (textCarAddress != null) {
			textCarAddress.setTextColor(isGlass ? gc.getTextSecondary() : TEXT_MUTED);
		}
		if (textWalkingTime != null) {
			textWalkingTime.setTextColor(isGlass ? gc.getTextSecondary() : TEXT_MUTED);
		}
		if (textNote != null) {
			textNote.setTextColor(isGlass ? gc.getTextPrimary() : 0xFFFFFFFF);
			if (isGlass) {
				textNote.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassCardDrawable(ctx, 8f, isNight));
			}
		}
		if (cardFoundCar instanceof TextView btnFound) {
			btnFound.setTextColor(isGlass ? gc.getTextSecondary() : TEXT_MUTED);
			if (isGlass) {
				btnFound.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassPillDrawable(ctx, 21f, isNight));
			}
		}
		if (btnRadar != null && isGlass) {
			btnRadar.setTextColor(isNight ? 0xFF00E5FF : 0xFF007A87);
			float density = getResources().getDisplayMetrics().density;
			GradientDrawable gd = new GradientDrawable();
			gd.setColor(isNight ? 0x2500E5FF : 0x1800E5FF);
			gd.setCornerRadius(24f * density);
			gd.setStroke(Math.round(1.5f * density), isNight ? 0xFF00E5FF : 0xFF007A87);
			btnRadar.setBackground(gd);
		}
		if (btnDirections != null && isGlass) {
			btnDirections.setColorFilter(isNight ? 0xFF00E5FF : 0xFF007A87);
			btnDirections.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassPillDrawable(ctx, 24f, isNight));
		}
		if (radarPillText != null) {
			radarPillText.setTextColor(isGlass ? (isNight ? 0xFF00E5FF : 0xFF007A87) : 0xFFFFFFFF);
		}
		if (radarCenterPill != null && isGlass) {
			float density = getResources().getDisplayMetrics().density;
			GradientDrawable pillBg = new GradientDrawable();
			pillBg.setColor(isNight ? 0x2500E5FF : 0x1800E5FF);
			pillBg.setStroke(Math.round(1 * density), isNight ? 0xFF00E5FF : 0xFF007A87);
			pillBg.setCornerRadius(18f * density);
			radarCenterPill.setBackground(pillBg);
		}
		if (radarBackButton != null && isGlass) {
			radarBackButton.setColorFilter(isNight ? 0xFFFFFFFF : 0xFF15181A);
			radarBackButton.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassPillDrawable(ctx, 20f, isNight));
		}
		if (textRadarDistance != null) {
			textRadarDistance.setTextColor(isGlass ? (isNight ? 0xFFFFFFFF : 0xFF0F172A) : 0xFFFFFFFF);
		}
		if (textRadarUnit != null) {
			textRadarUnit.setTextColor(isGlass ? gc.getTextSecondary() : TEXT_MUTED);
		}
		if (textRadarSubHeader != null) {
			textRadarSubHeader.setTextColor(isGlass ? gc.getTextSecondary() : TEXT_MUTED);
		}
		if (textRadarAccuracy != null) {
			textRadarAccuracy.setTextColor(isGlass ? gc.getTextSecondary() : TEXT_MUTED);
		}
		if (btnViewOnMap != null && isGlass) {
			btnViewOnMap.setTextColor(isNight ? 0xFFFFFFFF : 0xFF15181A);
			btnViewOnMap.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassPillDrawable(ctx, 24f, isNight));
		}
		if (btnRadarFound != null && isGlass) {
			btnRadarFound.setTextColor(gc.getTextSecondary());
			btnRadarFound.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassPillDrawable(ctx, 20f, isNight));
		}
	}

	private void requestLocationPermissionsIfNeeded() {
		Context ctx = getContext();
		if (ctx == null) return;
		if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
				&& androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
			MainActivityDelegate a = getActivityDelegate();
			if (a != null && a.getAppActivity() != null) {
				a.getAppActivity().checkPermissions(
						android.Manifest.permission.ACCESS_FINE_LOCATION,
						android.Manifest.permission.ACCESS_COARSE_LOCATION
				).onCompletion((res, err) -> {
					if (isAdded()) {
						currentUserLocation = CarLocatorManager.get().queryBestKnownUserLocation(requireContext());
						CarLocatorManager.get().startTrackingUserLocation(requireContext(), this);
						renderParkingStateToViews();
					}
				});
			}
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		MainActivityDelegate a = getActivityDelegate();
		if (a != null) {
			a.removeBroadcastListener(this);
		}
		CarFinderPrefs.unregisterListener(requireContext(), this);
		CarLocatorManager.get().stopTrackingUserLocation(this);
		if (compassManager != null) {
			compassManager.stop();
		}
	}

	@Override
	public boolean onBackPressed() {
		if (isRadarMode) {
			setRadarModeVisible(false);
			return true;
		}
		return super.onBackPressed();
	}

	private void setupOverviewModeLayout(float density) {
		boolean isGlass = isGlassTheme();
		boolean isNight = me.aap.fermata.ui.glass.theme.GlassThemeColors.isNight(requireContext());
		me.aap.fermata.ui.glass.theme.GlassThemeColors gc =
				me.aap.fermata.ui.glass.theme.GlassThemeColors.get(requireContext(), isNight);

		overviewContainer = new LinearLayout(requireContext());
		overviewContainer.setOrientation(LinearLayout.VERTICAL);
		overviewContainer.setBackgroundColor(isGlass ? (isNight ? 0xFF07090D : 0xFFF8FAFC) : 0xFF07090D);

		// Header Bar
		LinearLayout header = new LinearLayout(requireContext());
		this.overviewHeader = header;
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(Math.round(16 * density), Math.round(10 * density), Math.round(16 * density), Math.round(10 * density));
		if (isGlass) {
			header.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassCardDrawable(requireContext(), 0f, isNight));
		}

		ImageView backBtn = (ImageView) createCircularBackButton(density);
		this.overviewBackButton = backBtn;
		backBtn.setOnClickListener(v -> {
			MainActivityDelegate a = getActivityDelegate();
			if (a != null) a.onBackPressed();
		});
		LinearLayout.LayoutParams pBack = new LinearLayout.LayoutParams(Math.round(40 * density), Math.round(40 * density));
		pBack.rightMargin = Math.round(12 * density);
		header.addView(backBtn, pBack);

		TextView title = new TextView(requireContext());
		this.textHeaderTitle = title;
		title.setText(R.string.car_finder_title);
		title.setTextSize(20);
		title.setTextColor(isGlass ? gc.getTextPrimary() : 0xFFFFFFFF);
		title.setTypeface(null, Typeface.BOLD);
		header.addView(title, new LinearLayout.LayoutParams(0, -2, 1.0f));

		// In-page Settings Button using Fermata's official settings gear drawable
		ImageView btnSettings = new ImageView(requireContext());
		this.btnSettings = btnSettings;
		btnSettings.setImageResource(R.drawable.settings_gear);
		btnSettings.setColorFilter(isGlass ? gc.getTextPrimary() : 0xFFC3CDDE);
		btnSettings.setPadding(Math.round(8 * density), Math.round(8 * density), Math.round(8 * density), Math.round(8 * density));
		btnSettings.setOnClickListener(v -> CarFinderSettingsDialog.displaySettingsDialog(requireContext(), new CarFinderSettingsDialog.OnSettingsChangeListener() {
			@Override
			public void onSettingsChanged() {
				renderParkingStateToViews();
			}

			@Override
			public void onLocationCleared() {
				currentParkingLocation = null;
				renderParkingStateToViews();
			}
		}));
		header.addView(btnSettings, new LinearLayout.LayoutParams(Math.round(40 * density), Math.round(40 * density)));
		overviewContainer.addView(header, new LinearLayout.LayoutParams(-1, -2));

		// Map Canvas View (Takes top remaining height)
		mapTileView = new CarMapTileView(requireContext());
		mapTileView.setNightMode(isNight);
		overviewContainer.addView(mapTileView, new LinearLayout.LayoutParams(-1, 0, 1.0f));

		// Bottom Card Container (Material 3 rounded bottom sheet in Fermata dark navy / Glass)
		LinearLayout cardContainer = new LinearLayout(requireContext());
		this.cardContainer = cardContainer;
		cardContainer.setOrientation(LinearLayout.VERTICAL);
		if (isGlass) {
			cardContainer.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassCardDrawable(requireContext(), 24f, isNight));
		} else {
			GradientDrawable sheetBg = new GradientDrawable();
			sheetBg.setColor(SURFACE_DARK);
			float cornerR = 24f * density;
			sheetBg.setCornerRadii(new float[]{cornerR, cornerR, cornerR, cornerR, 0f, 0f, 0f, 0f});
			sheetBg.setStroke(Math.round(1 * density), CARD_BORDER);
			cardContainer.setBackground(sheetBg);
		}
		cardContainer.setPadding(Math.round(20 * density), Math.round(18 * density), Math.round(20 * density), Math.round(18 * density));

		// Empty state
		cardEmpty = new LinearLayout(requireContext());
		((LinearLayout) cardEmpty).setOrientation(LinearLayout.VERTICAL);
		textEmptyStatus = new TextView(requireContext());
		textEmptyStatus.setText(R.string.car_finder_no_location);
		textEmptyStatus.setTextSize(14);
		textEmptyStatus.setTextColor(TEXT_MUTED);
		((LinearLayout) cardEmpty).addView(textEmptyStatus);

		LinearLayout emptyActions = new LinearLayout(requireContext());
		emptyActions.setOrientation(LinearLayout.HORIZONTAL);
		emptyActions.setPadding(0, Math.round(12 * density), 0, 0);

		TextView btnPinManual = createStyledPillButton("+ " + getString(R.string.car_finder_pin_here), 0xFFFFFFFF, ACCENT_BLUE, 0, v -> {
			Location loc = (currentUserLocation != null) ? currentUserLocation : CarLocatorManager.get().queryBestKnownUserLocation(requireContext());
			if (loc != null) {
				currentUserLocation = loc;
				CarLocatorManager.get().saveManualParkingLocation(requireContext(), loc, null);
				Toast.makeText(requireContext(), R.string.car_finder_pinned_success, Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(requireContext(), R.string.car_finder_waiting_gps, Toast.LENGTH_SHORT).show();
			}
		});
		this.btnPinManual = btnPinManual;
		btnPinManual.setPadding(Math.round(16 * density), 0, Math.round(16 * density), 0);
		emptyActions.addView(btnPinManual, new LinearLayout.LayoutParams(-2, Math.round(42 * density)));

		LinearLayout btnEmptyPhoto = new LinearLayout(requireContext());
		this.btnEmptyPhoto = btnEmptyPhoto;
		btnEmptyPhoto.setOrientation(LinearLayout.HORIZONTAL);
		btnEmptyPhoto.setGravity(Gravity.CENTER_VERTICAL);
		btnEmptyPhoto.setPadding(Math.round(12 * density), Math.round(8 * density), Math.round(12 * density), Math.round(8 * density));
		btnEmptyPhoto.setOnClickListener(v -> captureParkingLocationPhoto());

		ImageView iconEmptyCam = new ImageView(requireContext());
		iconEmptyCam.setImageResource(R.drawable.ic_camera);
		iconEmptyCam.setColorFilter(ACCENT_BLUE);
		btnEmptyPhoto.addView(iconEmptyCam, new LinearLayout.LayoutParams(Math.round(18 * density), Math.round(18 * density)));

		TextView textEmptyCam = new TextView(requireContext());
		textEmptyCam.setText("  " + getString(R.string.car_finder_take_photo));
		textEmptyCam.setTextColor(ACCENT_BLUE);
		textEmptyCam.setTextSize(14);
		btnEmptyPhoto.addView(textEmptyCam);
		emptyActions.addView(btnEmptyPhoto, new LinearLayout.LayoutParams(-2, -2));

		((LinearLayout) cardEmpty).addView(emptyActions);
		cardContainer.addView(cardEmpty, new LinearLayout.LayoutParams(-1, -2));

		// Active parked info
		cardActiveInfo = new LinearLayout(requireContext());
		((LinearLayout) cardActiveInfo).setOrientation(LinearLayout.VERTICAL);

		// Row 1: Left Car Badge + Title & Address + Right Distance & Walk
		LinearLayout infoRow = new LinearLayout(requireContext());
		infoRow.setOrientation(LinearLayout.HORIZONTAL);
		infoRow.setGravity(Gravity.CENTER_VERTICAL);

		// Left Circular Car Badge (Fermata electric blue style)
		ImageView carBadge = new ImageView(requireContext());
		carBadge.setImageResource(R.drawable.ic_car_find);
		carBadge.setColorFilter(ACCENT_BLUE);
		carBadge.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		int badgePad = Math.round(11 * density);
		carBadge.setPadding(badgePad, badgePad, badgePad, badgePad);
		GradientDrawable badgeBg = new GradientDrawable();
		badgeBg.setShape(GradientDrawable.OVAL);
		badgeBg.setColor(CARD_BUTTON_BG);
		badgeBg.setStroke(Math.round(1 * density), CARD_BUTTON_BORDER);
		carBadge.setBackground(badgeBg);
		infoRow.addView(carBadge, new LinearLayout.LayoutParams(Math.round(44 * density), Math.round(44 * density)));

		// Center: Title + Confidence Badge + Address
		LinearLayout textCol = new LinearLayout(requireContext());
		textCol.setOrientation(LinearLayout.VERTICAL);
		textCol.setPadding(Math.round(12 * density), 0, Math.round(8 * density), 0);

		LinearLayout titleRow = new LinearLayout(requireContext());
		titleRow.setOrientation(LinearLayout.HORIZONTAL);
		titleRow.setGravity(Gravity.CENTER_VERTICAL);

		textCarTitle = new TextView(requireContext());
		textCarTitle.setText(R.string.car_finder_my_car);
		textCarTitle.setTextSize(17);
		textCarTitle.setTextColor(isGlass ? gc.getTextPrimary() : 0xFFFFFFFF);
		textCarTitle.setTypeface(null, Typeface.BOLD);
		titleRow.addView(textCarTitle, new LinearLayout.LayoutParams(-2, -2));

		textConfidenceBadge = new TextView(requireContext());
		textConfidenceBadge.setTextSize(10);
		textConfidenceBadge.setTypeface(null, Typeface.BOLD);
		textConfidenceBadge.setPadding(Math.round(6 * density), Math.round(2 * density), Math.round(6 * density), Math.round(2 * density));
		LinearLayout.LayoutParams pBadge = new LinearLayout.LayoutParams(-2, -2);
		pBadge.leftMargin = Math.round(6 * density);
		titleRow.addView(textConfidenceBadge, pBadge);
		textCol.addView(titleRow);

		textCarAddress = new TextView(requireContext());
		textCarAddress.setTextColor(isGlass ? gc.getTextSecondary() : TEXT_MUTED);
		textCarAddress.setTextSize(13);
		textCarAddress.setMaxLines(1);
		textCarAddress.setEllipsize(TextUtils.TruncateAt.END);
		textCarAddress.setPadding(0, Math.round(2 * density), 0, 0);
		textCol.addView(textCarAddress);

		infoRow.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1.0f));

		// Right: Distance + Unit & Walking time
		LinearLayout distCol = new LinearLayout(requireContext());
		distCol.setOrientation(LinearLayout.VERTICAL);
		distCol.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

		textDistance = new TextView(requireContext());
		textDistance.setTextSize(26);
		textDistance.setTextColor(ACCENT_BLUE);
		textDistance.setTypeface(null, Typeface.BOLD);
		distCol.addView(textDistance, new LinearLayout.LayoutParams(-2, -2));

		textWalkingTime = new TextView(requireContext());
		textWalkingTime.setTextColor(isGlass ? gc.getTextSecondary() : TEXT_MUTED);
		textWalkingTime.setTextSize(11);
		distCol.addView(textWalkingTime, new LinearLayout.LayoutParams(-2, -2));

		infoRow.addView(distCol, new LinearLayout.LayoutParams(-2, -2));
		((LinearLayout) cardActiveInfo).addView(infoRow);

		// Basement Hint (if present)
		textBasementHint = new TextView(requireContext());
		textBasementHint.setTextSize(12);
		textBasementHint.setTextColor(0xFF93C5FD);
		textBasementHint.setPadding(Math.round(10 * density), Math.round(5 * density), Math.round(10 * density), Math.round(5 * density));
		GradientDrawable baseBg = new GradientDrawable();
		baseBg.setColor(0x223B82F6);
		baseBg.setStroke(Math.round(1 * density), 0x443B82F6);
		baseBg.setCornerRadius(6f * density);
		textBasementHint.setBackground(baseBg);
		textBasementHint.setVisibility(View.GONE);
		LinearLayout.LayoutParams pHint = new LinearLayout.LayoutParams(-1, -2);
		pHint.topMargin = Math.round(8 * density);
		((LinearLayout) cardActiveInfo).addView(textBasementHint, pHint);

		// Action Buttons row: [▲ Tìm chính xác] (pill button) + [Directions] (round button)
		LinearLayout actionsRow = new LinearLayout(requireContext());
		actionsRow.setOrientation(LinearLayout.HORIZONTAL);
		actionsRow.setGravity(Gravity.CENTER_VERTICAL);
		actionsRow.setPadding(0, Math.round(14 * density), 0, Math.round(6 * density));

		// Main Button: Tìm chính xác (Fermata Electric Blue)
		TextView btnRadar = createStyledPillButton("▲  " + getString(R.string.car_finder_precise_find), 0xFFFFFFFF, ACCENT_BLUE, 0, v -> setRadarModeVisible(true));
		this.btnRadar = btnRadar;
		actionsRow.addView(btnRadar, new LinearLayout.LayoutParams(0, Math.round(48 * density), 1.0f));

		// Circle Button: Chỉ đường (Google Maps) with subtle blue highlight
		LinearLayout.LayoutParams pMaps = new LinearLayout.LayoutParams(Math.round(48 * density), Math.round(48 * density));
		pMaps.leftMargin = Math.round(12 * density);
		ImageView btnDirections = createCircularActionButton(R.drawable.ic_nav_arrow, ACCENT_BLUE, CARD_BUTTON_BG, CARD_BUTTON_BORDER, v -> launchWalkingNavigationInExternalMap());
		this.btnDirections = btnDirections;
		actionsRow.addView(btnDirections, pMaps);
		((LinearLayout) cardActiveInfo).addView(actionsRow);

		// Photo & Note Section
		setupPhotoAndNoteSection(density);
		((LinearLayout) cardActiveInfo).addView(photoNoteSection);

		// Button: ĐÃ TÌM THẤY XE (Secondary Card Action Button)
		cardFoundCar = createStyledPillButton(getString(R.string.car_finder_found_car), TEXT_MUTED, CARD_SURFACE, CARD_BORDER, v -> promptCarFoundConfirmationAndReset());
		LinearLayout.LayoutParams pFound = new LinearLayout.LayoutParams(-1, Math.round(42 * density));
		pFound.topMargin = Math.round(8 * density);
		((LinearLayout) cardActiveInfo).addView(cardFoundCar, pFound);

		cardContainer.addView(cardActiveInfo, new LinearLayout.LayoutParams(-1, -2));
		overviewContainer.addView(cardContainer, new LinearLayout.LayoutParams(-1, -2));
	}

	private void setupPhotoAndNoteSection(float density) {
		photoNoteSection = new LinearLayout(requireContext());
		photoNoteSection.setOrientation(LinearLayout.VERTICAL);
		photoNoteSection.setPadding(0, Math.round(8 * density), 0, Math.round(4 * density));

		// Photo layout
		LinearLayout photoRow = new LinearLayout(requireContext());
		photoRow.setOrientation(LinearLayout.HORIZONTAL);
		photoRow.setGravity(Gravity.CENTER_VERTICAL);

		imgPhotoThumb = new ImageView(requireContext());
		imgPhotoThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
		imgPhotoThumb.setBackgroundColor(CARD_SURFACE);
		imgPhotoThumb.setOnClickListener(v -> {
			if (currentParkingLocation != null && currentParkingLocation.getPhotoPath() != null) {
				displayFullPhotoViewerDialog(currentParkingLocation.getPhotoPath());
			}
		});
		photoRow.addView(imgPhotoThumb, new LinearLayout.LayoutParams(Math.round(80 * density), Math.round(52 * density)));

		layoutPhotoActions = new LinearLayout(requireContext());
		layoutPhotoActions.setOrientation(LinearLayout.VERTICAL);
		layoutPhotoActions.setPadding(Math.round(12 * density), 0, 0, 0);

		TextView btnRetake = new TextView(requireContext());
		btnRetake.setText(R.string.car_finder_retake_photo);
		btnRetake.setTextColor(ACCENT_BLUE);
		btnRetake.setTextSize(13);
		btnRetake.setPadding(0, 0, 0, Math.round(4 * density));
		btnRetake.setOnClickListener(v -> captureParkingLocationPhoto());
		layoutPhotoActions.addView(btnRetake);

		TextView btnDeletePhoto = new TextView(requireContext());
		btnDeletePhoto.setText(R.string.car_finder_delete_photo);
		btnDeletePhoto.setTextColor(0xFFFF5252);
		btnDeletePhoto.setTextSize(13);
		btnDeletePhoto.setOnClickListener(v -> removeParkingLocationPhoto());
		layoutPhotoActions.addView(btnDeletePhoto);

		photoRow.addView(layoutPhotoActions, new LinearLayout.LayoutParams(0, -2, 1.0f));

		// "Chụp ảnh điểm đỗ" button with clean vector camera icon
		btnTakePhoto = new LinearLayout(requireContext());
		btnTakePhoto.setOrientation(LinearLayout.HORIZONTAL);
		btnTakePhoto.setGravity(Gravity.CENTER_VERTICAL);
		btnTakePhoto.setPadding(0, Math.round(6 * density), 0, Math.round(4 * density));
		btnTakePhoto.setOnClickListener(v -> captureParkingLocationPhoto());

		ImageView iconCam = new ImageView(requireContext());
		iconCam.setImageResource(R.drawable.ic_camera);
		iconCam.setColorFilter(ACCENT_BLUE);
		btnTakePhoto.addView(iconCam, new LinearLayout.LayoutParams(Math.round(18 * density), Math.round(18 * density)));

		TextView textCam = new TextView(requireContext());
		textCam.setText("  " + getString(R.string.car_finder_take_photo));
		textCam.setTextColor(ACCENT_BLUE);
		textCam.setTextSize(13);
		btnTakePhoto.addView(textCam);

		photoNoteSection.addView(photoRow);
		photoNoteSection.addView(btnTakePhoto);

		// Note Text
		textNote = new TextView(requireContext());
		textNote.setTextSize(13);
		textNote.setPadding(0, Math.round(6 * density), 0, 0);
		textNote.setOnClickListener(v -> displayNoteEditorDialog());
		photoNoteSection.addView(textNote);
	}

	private void setupRadarModeLayout(float density) {
		radarContainer = new LinearLayout(requireContext());
		radarContainer.setOrientation(LinearLayout.VERTICAL);
		radarContainer.setBackgroundColor(0xFF07090D);
		radarContainer.setVisibility(View.GONE);

		// Header Bar with Back Button + Centered Pill
		LinearLayout header = new LinearLayout(requireContext());
		this.radarHeader = header;
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(Math.round(16 * density), Math.round(12 * density), Math.round(16 * density), Math.round(12 * density));

		ImageView backBtn = (ImageView) createCircularBackButton(density);
		this.radarBackButton = backBtn;
		header.addView(backBtn, new LinearLayout.LayoutParams(Math.round(40 * density), Math.round(40 * density)));

		// Center Pill: "▲ Tìm chính xác"
		LinearLayout centerPill = new LinearLayout(requireContext());
		this.radarCenterPill = centerPill;
		centerPill.setOrientation(LinearLayout.HORIZONTAL);
		centerPill.setGravity(Gravity.CENTER);
		GradientDrawable pillBg = new GradientDrawable();
		pillBg.setColor(CARD_BUTTON_BG);
		pillBg.setStroke(Math.round(1 * density), CARD_BORDER);
		pillBg.setCornerRadius(18f * density);
		centerPill.setBackground(pillBg);
		centerPill.setPadding(Math.round(16 * density), Math.round(6 * density), Math.round(16 * density), Math.round(6 * density));

		TextView pillText = new TextView(requireContext());
		this.radarPillText = pillText;
		pillText.setText("▲  " + getString(R.string.car_finder_precise_find));
		pillText.setTextColor(0xFFFFFFFF);
		pillText.setTextSize(13);
		pillText.setTypeface(null, Typeface.BOLD);
		centerPill.addView(pillText);

		LinearLayout.LayoutParams pCenter = new LinearLayout.LayoutParams(0, -2, 1.0f);
		pCenter.gravity = Gravity.CENTER;
		centerPill.setLayoutParams(pCenter);
		header.addView(centerPill);

		// Spacer on right to balance back button
		View spacer = new View(requireContext());
		header.addView(spacer, new LinearLayout.LayoutParams(Math.round(40 * density), 1));
		radarContainer.addView(header, new LinearLayout.LayoutParams(-1, -2));

		// Radar Canvas View (takes flex 1)
		if (isGlassTheme()) {
			glassRadarView = new me.aap.fermata.ui.glass.carfinder.GlassRadarView(requireContext());
			radarContainer.addView(glassRadarView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
		} else {
			radarView = new CarRadarView(requireContext());
			radarContainer.addView(radarView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
		}

		// Bottom Info & Controls
		LinearLayout bottomRadar = new LinearLayout(requireContext());
		bottomRadar.setOrientation(LinearLayout.VERTICAL);
		bottomRadar.setGravity(Gravity.CENTER_HORIZONTAL);
		bottomRadar.setPadding(Math.round(24 * density), 0, Math.round(24 * density), Math.round(20 * density));

		textRadarSubHeader = new TextView(requireContext());
		textRadarSubHeader.setText(R.string.car_finder_radar_sub_here);
		textRadarSubHeader.setTextSize(12);
		textRadarSubHeader.setTextColor(TEXT_MUTED);
		textRadarSubHeader.setTypeface(null, Typeface.BOLD);
		textRadarSubHeader.setLetterSpacing(0.08f);
		textRadarSubHeader.setGravity(Gravity.CENTER);
		bottomRadar.addView(textRadarSubHeader);

		// Distance Row: Giant number + Unit & Direction
		LinearLayout distRow = new LinearLayout(requireContext());
		distRow.setOrientation(LinearLayout.HORIZONTAL);
		distRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
		distRow.setPadding(0, Math.round(4 * density), 0, Math.round(4 * density));

		textRadarDistance = new TextView(requireContext());
		textRadarDistance.setTextSize(54);
		textRadarDistance.setTextColor(0xFFFFFFFF);
		textRadarDistance.setTypeface(null, Typeface.BOLD);
		distRow.addView(textRadarDistance, new LinearLayout.LayoutParams(-2, -2));

		LinearLayout unitDirCol = new LinearLayout(requireContext());
		unitDirCol.setOrientation(LinearLayout.VERTICAL);
		unitDirCol.setPadding(Math.round(10 * density), 0, 0, 0);

		textRadarUnit = new TextView(requireContext());
		textRadarUnit.setText(R.string.unit_m);
		textRadarUnit.setTextSize(14);
		textRadarUnit.setTextColor(TEXT_MUTED);
		unitDirCol.addView(textRadarUnit);

		textRadarDirection = new TextView(requireContext());
		textRadarDirection.setTextSize(15);
		textRadarDirection.setTextColor(ACCENT_BLUE);
		textRadarDirection.setTypeface(null, Typeface.BOLD);
		unitDirCol.addView(textRadarDirection);

		distRow.addView(unitDirCol);
		bottomRadar.addView(distRow);

		// Status / Accuracy line
		textRadarAccuracy = new TextView(requireContext());
		textRadarAccuracy.setTextSize(12);
		textRadarAccuracy.setTextColor(TEXT_MUTED);
		textRadarAccuracy.setGravity(Gravity.CENTER);
		textRadarAccuracy.setPadding(0, Math.round(4 * density), 0, Math.round(14 * density));
		bottomRadar.addView(textRadarAccuracy);

		// Big Pill Button: "Xem trên bản đồ"
		TextView btnViewOnMap = createStyledPillButton(getString(R.string.car_finder_view_on_map), 0xFFFFFFFF, CARD_BUTTON_BG, CARD_BUTTON_BORDER, v -> setRadarModeVisible(false));
		this.btnViewOnMap = btnViewOnMap;
		bottomRadar.addView(btnViewOnMap, new LinearLayout.LayoutParams(-1, Math.round(48 * density)));

		// Secondary "Đã tìm thấy xe"
		TextView btnRadarFound = createStyledPillButton(getString(R.string.car_finder_found_car), TEXT_MUTED, CARD_SURFACE, CARD_BORDER, v -> promptCarFoundConfirmationAndReset());
		this.btnRadarFound = btnRadarFound;
		LinearLayout.LayoutParams pFound = new LinearLayout.LayoutParams(-1, Math.round(40 * density));
		pFound.topMargin = Math.round(8 * density);
		bottomRadar.addView(btnRadarFound, pFound);

		radarContainer.addView(bottomRadar, new LinearLayout.LayoutParams(-1, -2));
	}

	private TextView createStyledPillButton(String text, int textColor, int bgColor, int strokeColor, View.OnClickListener onClick) {
		float density = getResources().getDisplayMetrics().density;
		TextView btn = new TextView(requireContext());
		btn.setText(text);
		boolean isGlass = isGlassTheme();
		if (isGlass) {
			boolean isNight = me.aap.fermata.ui.glass.theme.GlassThemeColors.isNight(requireContext());
			if (bgColor == ACCENT_BLUE) {
				textColor = isNight ? 0xFF00E5FF : 0xFF007A87;
			} else {
				textColor = isNight ? 0xFFFFFFFF : 0xFF15181A;
			}
		}
		btn.setTextColor(textColor);
		btn.setTextSize(15);
		btn.setTypeface(null, Typeface.BOLD);
		btn.setGravity(Gravity.CENTER);

		if (isGlass) {
			boolean isNight = me.aap.fermata.ui.glass.theme.GlassThemeColors.isNight(requireContext());
			if (bgColor == ACCENT_BLUE) {
				GradientDrawable gd = new GradientDrawable();
				gd.setColor(isNight ? 0x2500E5FF : 0x1800E5FF);
				gd.setCornerRadius(24f * density);
				gd.setStroke(Math.round(1.5f * density), isNight ? 0xFF00E5FF : 0xFF007A87);
				btn.setBackground(gd);
			} else {
				btn.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassPillDrawable(requireContext(), 24f, isNight));
			}
		} else {
			GradientDrawable gd = new GradientDrawable();
			gd.setColor(bgColor);
			gd.setCornerRadius(24f * density);
			if (strokeColor != 0) {
				gd.setStroke(Math.round(1f * density), strokeColor);
			}
			btn.setBackground(gd);
		}

		btn.setClickable(true);
		btn.setFocusable(true);
		btn.setOnClickListener(onClick);
		return btn;
	}

	private ImageView createCircularActionButton(int iconRes, int iconColor, int bgColor, int strokeColor, View.OnClickListener onClick) {
		float density = getResources().getDisplayMetrics().density;
		ImageView iv = new ImageView(requireContext());
		iv.setImageResource(iconRes);
		iv.setColorFilter(iconColor);
		iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		int pad = Math.round(13 * density);
		iv.setPadding(pad, pad, pad, pad);

		GradientDrawable gd = new GradientDrawable();
		gd.setShape(GradientDrawable.OVAL);
		gd.setColor(bgColor);
		if (strokeColor != 0) {
			gd.setStroke(Math.round(1.5f * density), strokeColor);
		}
		iv.setBackground(gd);

		iv.setClickable(true);
		iv.setFocusable(true);
		iv.setOnClickListener(onClick);
		return iv;
	}

	private View createCircularBackButton(float density) {
		ImageView btn = new ImageView(requireContext());
		btn.setImageResource(R.drawable.ic_arrow_back);
		btn.setColorFilter(0xFFFFFFFF);
		btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		int pad = Math.round(10 * density);
		btn.setPadding(pad, pad, pad, pad);

		GradientDrawable gd = new GradientDrawable();
		gd.setShape(GradientDrawable.OVAL);
		gd.setColor(CARD_BUTTON_BG);
		gd.setStroke(Math.round(1f * density), CARD_BORDER);
		btn.setBackground(gd);

		btn.setClickable(true);
		btn.setFocusable(true);
		btn.setOnClickListener(v -> setRadarModeVisible(false));
		return btn;
	}

	/**
	 * Switches UI mode between Map Overview and Precise Radar.
	 */
	public void setRadarModeVisible(boolean isRadarVisible) {
		this.isRadarMode = isRadarVisible;
		if (isRadarVisible) {
			overviewContainer.setVisibility(View.GONE);
			radarContainer.setVisibility(View.VISIBLE);
			if (compassManager != null) compassManager.start();
		} else {
			radarContainer.setVisibility(View.GONE);
			overviewContainer.setVisibility(View.VISIBLE);
			if (compassManager != null) compassManager.stop();
		}
		renderParkingStateToViews();
	}

	/** Backwards-compatible alias */
	public void switchMode(boolean radar) {
		setRadarModeVisible(radar);
	}

	/**
	 * Synchronizes the visual state of all cards, maps, and radar with current parking & user data.
	 */
	public void renderParkingStateToViews() {
		if (overviewBackButton != null) {
			MainActivityDelegate a = getActivityDelegate();
			boolean canBack = a != null && (a.isCarActivity() || !PhoneRootPolicy.isPhoneRoot(R.id.car_finder_fragment));
			overviewBackButton.setVisibility(canBack ? View.VISIBLE : View.GONE);
		}
		if (currentParkingLocation == null || !currentParkingLocation.isValid()) {
			cardEmpty.setVisibility(View.VISIBLE);
			cardActiveInfo.setVisibility(View.GONE);
			mapTileView.updateVehicleAndUserLocations(null, currentUserLocation);
			if (isRadarMode) setRadarModeVisible(false);
			return;
		}

		cardEmpty.setVisibility(View.GONE);
		cardActiveInfo.setVisibility(View.VISIBLE);

		mapTileView.updateVehicleAndUserLocations(currentParkingLocation, currentUserLocation);

		String addr = currentParkingLocation.getAddress();
		textCarAddress.setText(addr.isEmpty() ? getString(R.string.car_finder_address_resolving) : addr);

		// Update Confidence Badge & Basement Hint
		float density = getResources().getDisplayMetrics().density;
		if (currentParkingLocation.isBasement()) {
			textConfidenceBadge.setText("● " + getString(R.string.car_finder_conf_basement));
			textConfidenceBadge.setTextColor(0xFF93C5FD);
			GradientDrawable cBg = new GradientDrawable();
			cBg.setColor(0x223B82F6);
			cBg.setStroke(Math.round(1f * density), 0x443B82F6);
			cBg.setCornerRadius(6f * density);
			textConfidenceBadge.setBackground(cBg);
			textConfidenceBadge.setVisibility(View.VISIBLE);

			textBasementHint.setText(getString(R.string.car_finder_basement_hint));
			textBasementHint.setVisibility(View.VISIBLE);
		} else {
			textBasementHint.setVisibility(View.GONE);
			float unc = currentParkingLocation.getUncertainty();
			if (unc <= 0f) unc = currentParkingLocation.getAccuracy();
			int uncM = Math.round(unc);

			GradientDrawable cBg = new GradientDrawable();
			cBg.setCornerRadius(6f * density);
			if (unc <= 15.0f) {
				textConfidenceBadge.setText("● " + getString(R.string.car_finder_conf_high, uncM));
				textConfidenceBadge.setTextColor(0xFF60A5FA);
				cBg.setColor(0x253B82F6);
				cBg.setStroke(Math.round(1f * density), 0x443B82F6);
			} else if (unc <= 35.0f) {
				textConfidenceBadge.setText("● " + getString(R.string.car_finder_conf_med, uncM));
				textConfidenceBadge.setTextColor(TEXT_MUTED);
				cBg.setColor(0x22131926);
				cBg.setStroke(Math.round(1f * density), CARD_BORDER);
			} else {
				textConfidenceBadge.setText("● " + getString(R.string.car_finder_conf_med, uncM));
				textConfidenceBadge.setTextColor(TEXT_MUTED);
				cBg.setColor(0x22131926);
				cBg.setStroke(Math.round(1f * density), CARD_BORDER);
			}
			textConfidenceBadge.setBackground(cBg);
			textConfidenceBadge.setVisibility(View.VISIBLE);
		}

		// Update Photo & Note state
		String photoPath = currentParkingLocation.getPhotoPath();
		boolean hasPhoto = false;
		if (photoPath != null && !photoPath.isEmpty()) {
			File file = new File(photoPath);
			if (file.exists() && file.length() > 0) {
				Bitmap thumb = BitmapFactory.decodeFile(photoPath);
				if (thumb != null) {
					imgPhotoThumb.setImageBitmap(thumb);
					imgPhotoThumb.setVisibility(View.VISIBLE);
					layoutPhotoActions.setVisibility(View.VISIBLE);
					btnTakePhoto.setVisibility(View.GONE);
					hasPhoto = true;
				}
			}
		}

		if (!hasPhoto) {
			imgPhotoThumb.setVisibility(View.GONE);
			layoutPhotoActions.setVisibility(View.GONE);
			btnTakePhoto.setVisibility(View.VISIBLE);
		}

		String note = currentParkingLocation.getNote();
		if (note != null && !note.isEmpty()) {
			textNote.setText(note);
			textNote.setTextColor(isGlassTheme() ? me.aap.fermata.ui.glass.theme.GlassThemeColors.get(requireContext()).getTextPrimary() : 0xFFE2E8F0);
		} else {
			textNote.setText("+ " + getString(R.string.car_finder_add_note));
			textNote.setTextColor(isGlassTheme() ? me.aap.fermata.ui.glass.theme.GlassThemeColors.get(requireContext()).getTextSecondary() : TEXT_MUTED);
		}

		if (currentUserLocation != null) {
			float dist = AngleUtils.distanceBetween(
					currentUserLocation.getLatitude(), currentUserLocation.getLongitude(),
					currentParkingLocation.getLatitude(), currentParkingLocation.getLongitude());

			String unit = CarFinderPrefs.getDistanceUnit(requireContext());
			int walkMin = AngleUtils.estimateWalkingMinutes(dist);

			String distNumStr;
			String distUnitStr;
			if ("ft".equals(unit)) {
				int feet = Math.round(dist * 3.28084f);
				distNumStr = String.valueOf(feet);
				distUnitStr = getString(R.string.unit_ft);
			} else if (dist < 10.0f) {
				distNumStr = String.format(Locale.US, "%.1f", dist).replace('.', ',');
				distUnitStr = getString(R.string.unit_m);
			} else if (dist < 1000.0f) {
				distNumStr = String.valueOf(Math.round(dist));
				distUnitStr = getString(R.string.unit_m);
			} else {
				distNumStr = String.format(Locale.US, "%.1f", dist / 1000.0f).replace('.', ',');
				distUnitStr = getString(R.string.unit_km);
			}

			textDistance.setText(distNumStr);
			textWalkingTime.setText(distUnitStr + " " + getString(R.string.car_finder_walk_minutes, walkMin));

			textRadarDistance.setText(distNumStr);
			textRadarUnit.setText(distUnitStr);

			float bearing = AngleUtils.bearingBetween(
					currentUserLocation.getLatitude(), currentUserLocation.getLongitude(),
					currentParkingLocation.getLatitude(), currentParkingLocation.getLongitude());

			float relativeBearing = AngleUtils.normalizeAngle(bearing - currentAzimuth);
			updateRadarTelemetry(dist, relativeBearing);
		} else {
			textDistance.setText("--");
			textWalkingTime.setText("");
			textRadarDistance.setText("--");
			textRadarUnit.setText("");
			textRadarDirection.setText(R.string.car_finder_measuring_gps);
			updateRadarBearing(0f, false);
		}
	}

	/** Backwards-compatible alias */
	public void updateUiState() {
		renderParkingStateToViews();
	}

	private void updateRadarBearing(float relativeBearingDegrees, boolean valid) {
		if (glassRadarView != null) {
			glassRadarView.updateRelativeBearing(relativeBearingDegrees, valid);
		} else if (radarView != null) {
			radarView.updateRelativeBearing(relativeBearingDegrees, valid);
		}
	}

	private void updateRadarTelemetry(float distanceMeters, float relativeBearingDegrees) {
		updateRadarBearing(relativeBearingDegrees, true);

		float absAngle = Math.abs(relativeBearingDegrees);
		boolean isRight = relativeBearingDegrees >= 0f;

		String directionStr;
		if (absAngle <= 15f) {
			directionStr = getString(R.string.car_finder_straight_ahead);
		} else if (absAngle <= 60f) {
			directionStr = isRight ? getString(R.string.car_finder_front_right) : getString(R.string.car_finder_front_left);
		} else if (absAngle <= 120f) {
			directionStr = isRight ? getString(R.string.car_finder_right) : getString(R.string.car_finder_left);
		} else if (absAngle <= 165f) {
			directionStr = isRight ? getString(R.string.car_finder_rear_right) : getString(R.string.car_finder_rear_left);
		} else {
			directionStr = getString(R.string.car_finder_behind);
		}
		textRadarDirection.setText(directionStr);

		if (distanceMeters < 5f) {
			textRadarSubHeader.setText(R.string.car_finder_radar_sub_here);
			textRadarDirection.setText(getString(R.string.car_finder_right_here));
		} else if (absAngle <= 20f) {
			textRadarSubHeader.setText(R.string.car_finder_radar_sub_front);
		} else {
			textRadarSubHeader.setText(R.string.car_finder_radar_sub_arrow);
		}

		float totalError = (float) Math.sqrt(Math.pow(currentParkingLocation.getAccuracy(), 2) +
				(currentUserLocation != null ? Math.pow(currentUserLocation.getAccuracy(), 2) : 100));
		int errM = Math.round(totalError);
		String unitM = getString(R.string.unit_m);
		if (distanceMeters <= Math.max(10f, totalError)) {
			textRadarAccuracy.setText(getString(R.string.car_finder_radar_accuracy_in_range, errM, unitM));
		} else {
			textRadarAccuracy.setText("±" + errM + " " + unitM + "  ·  " + getString(R.string.car_finder_follow_arrow));
		}
	}

	/** Backwards-compatible alias */
	public void updateRadarState(float distance, float relativeBearing) {
		updateRadarTelemetry(distance, relativeBearing);
	}

	private void launchWalkingNavigationInExternalMap() {
		if (currentParkingLocation == null || !currentParkingLocation.isValid()) return;
		String latLng = currentParkingLocation.getLatitude() + "," + currentParkingLocation.getLongitude();

		if (currentUserLocation != null) {
			float dist = AngleUtils.distanceBetween(
					currentUserLocation.getLatitude(), currentUserLocation.getLongitude(),
					currentParkingLocation.getLatitude(), currentParkingLocation.getLongitude());
			if (dist <= 30) {
				Toast.makeText(requireContext(),
						getString(R.string.car_finder_near_car_hint, Math.round(dist)),
						Toast.LENGTH_LONG).show();
			}
		}

		// Google Maps walking route overview (dir/?api=1&travelmode=walking)
		Uri gmapsWalkingUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + latLng + "&travelmode=walking");
		try {
			Intent intent = new Intent(Intent.ACTION_VIEW, gmapsWalkingUri);
			intent.setPackage("com.google.android.apps.maps");
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		} catch (Throwable fallback1) {
			try {
				Intent genericIntent = new Intent(Intent.ACTION_VIEW, gmapsWalkingUri);
				genericIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(genericIntent);
			} catch (Throwable fallback2) {
				Intent geoIntent = new Intent(Intent.ACTION_VIEW,
						Uri.parse("geo:" + latLng + "?q=" + latLng + "(" + Uri.encode(getString(R.string.car_finder_my_car)) + ")"));
				geoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(geoIntent);
			}
		}
	}

	/** Backwards-compatible alias */
	public void openExternalNavigation() {
		launchWalkingNavigationInExternalMap();
	}

	private void captureParkingLocationPhoto() {
		try {
			File photoDir = new File(requireContext().getExternalFilesDir("parking_photos"), "");
			if (!photoDir.exists()) photoDir.mkdirs();
			File photoFile = new File(photoDir, "spot_" + System.currentTimeMillis() + ".jpg");

			Uri photoUri = FileProvider.getUriForFile(
					requireContext(),
					requireContext().getPackageName() + ".FileProvider",
					photoFile);

			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
			intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

			getActivityDelegate().startActivityForResult(() -> intent).onCompletion((res, err) -> {
				if (photoFile.exists() && photoFile.length() > 0) {
					if (currentParkingLocation == null || !currentParkingLocation.isValid()) {
						Location loc = (currentUserLocation != null) ? currentUserLocation : CarLocatorManager.get().queryBestKnownUserLocation(requireContext());
						if (loc != null) {
							currentParkingLocation = new CarParkingLocation(loc.getLatitude(), loc.getLongitude(), loc.getAccuracy(), System.currentTimeMillis());
						}
					}
					if (currentParkingLocation != null) {
						currentParkingLocation.setPhotoPath(photoFile.getAbsolutePath());
						CarFinderPrefs.saveParkingLocation(requireContext(), currentParkingLocation);
						renderParkingStateToViews();
					}
				}
			});
		} catch (Throwable t) {
			Toast.makeText(requireContext(), getString(R.string.car_finder_camera_error, String.valueOf(t.getMessage())), Toast.LENGTH_SHORT).show();
		}
	}

	/** Backwards-compatible alias */
	public void takeParkingPhoto() {
		captureParkingLocationPhoto();
	}

	private void removeParkingLocationPhoto() {
		if (currentParkingLocation != null && currentParkingLocation.getPhotoPath() != null) {
			try {
				new File(currentParkingLocation.getPhotoPath()).delete();
			} catch (Throwable ignored) {
			}
			currentParkingLocation.setPhotoPath(null);
			CarFinderPrefs.saveParkingLocation(requireContext(), currentParkingLocation);
			renderParkingStateToViews();
		}
	}

	/** Backwards-compatible alias */
	public void deleteParkingPhoto() {
		removeParkingLocationPhoto();
	}

	private void displayFullPhotoViewerDialog(String path) {
		File file = new File(path);
		if (!file.exists()) return;

		Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
		FrameLayout layout = new FrameLayout(requireContext());
		layout.setBackgroundColor(0xFF000000);

		ImageView fullImg = new ImageView(requireContext());
		fullImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
		Bitmap bmp = BitmapFactory.decodeFile(path);
		if (bmp != null) fullImg.setImageBitmap(bmp);
		layout.addView(fullImg, new FrameLayout.LayoutParams(-1, -1));

		TextView btnClose = new TextView(requireContext());
		btnClose.setText(R.string.car_finder_close);
		btnClose.setTextColor(0xFFFFFFFF);
		btnClose.setTextSize(16);
		btnClose.setTypeface(null, Typeface.BOLD);
		btnClose.setPadding(40, 60, 40, 40);
		btnClose.setOnClickListener(v -> dialog.dismiss());
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
		layout.addView(btnClose, lp);

		dialog.setContentView(layout);
		dialog.show();
	}

	/** Backwards-compatible alias */
	public void showPhotoDialog(String path) {
		displayFullPhotoViewerDialog(path);
	}

	private void displayNoteEditorDialog() {
		if (currentParkingLocation == null || !currentParkingLocation.isValid()) return;

		EditText input = new EditText(requireContext());
		input.setHint(R.string.car_finder_note_hint);
		if (currentParkingLocation.getNote() != null) {
			input.setText(currentParkingLocation.getNote());
			input.setSelection(input.getText().length());
		}
		input.setTextColor(0xFFFFFFFF);
		input.setHintTextColor(0xFF78909C);

		FrameLayout container = new FrameLayout(requireContext());
		int pad = Math.round(20 * getResources().getDisplayMetrics().density);
		container.setPadding(pad, pad / 2, pad, pad / 2);
		if (isGlassTheme()) {
			me.aap.fermata.ui.glass.theme.GlassThemeColors gc = me.aap.fermata.ui.glass.theme.GlassThemeColors.get(requireContext());
			input.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassInputDrawable(requireContext(), 8f));
			input.setTextColor(gc.getInputText());
			input.setHintTextColor(gc.getInputHint());
		}
		container.addView(input);

		AlertDialog noteDialog = new AlertDialog.Builder(requireContext())
				.setTitle(R.string.car_finder_add_note)
				.setView(container)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					currentParkingLocation.setNote(input.getText().toString().trim());
					CarFinderPrefs.saveParkingLocation(requireContext(), currentParkingLocation);
					renderParkingStateToViews();
				})
				.setNegativeButton(android.R.string.cancel, null)
				.create();
		if (isGlassTheme()) {
			me.aap.fermata.ui.glass.dialog.GlassDialogBuilder.styleAlertDialog(noteDialog, requireContext());
		}
		noteDialog.show();
	}

	/** Backwards-compatible alias */
	public void showNoteEditDialog() {
		displayNoteEditorDialog();
	}

	private void promptCarFoundConfirmationAndReset() {
		AlertDialog confirmDialog = new AlertDialog.Builder(requireContext())
				.setTitle(R.string.car_finder_found_confirm_title)
				.setMessage(R.string.car_finder_found_confirm_msg)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					if (currentParkingLocation != null && currentParkingLocation.getPhotoPath() != null) {
						try {
							new File(currentParkingLocation.getPhotoPath()).delete();
						} catch (Throwable ignored) {
						}
					}
					CarLocatorManager.get().clearParkingLocationOnCarFound(requireContext());
					Toast.makeText(requireContext(), R.string.car_finder_cleared_toast, Toast.LENGTH_SHORT).show();
					currentParkingLocation = null;
					setRadarModeVisible(false);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.create();
		if (isGlassTheme()) {
			me.aap.fermata.ui.glass.dialog.GlassDialogBuilder.styleAlertDialog(confirmDialog, requireContext());
		}
		confirmDialog.show();
	}

	/** Backwards-compatible alias */
	public void confirmCarFound() {
		promptCarFoundConfirmationAndReset();
	}

	@Override
	public void onUserLocationChanged(Location location) {
		this.currentUserLocation = location;
		if (compassManager != null) {
			compassManager.updateLocation(location);
		}
		if (isResumed()) {
			renderParkingStateToViews();
		}
	}

	@Override
	public void onHeadingChanged(float azimuthDegrees, boolean isCalibrated) {
		this.currentAzimuth = azimuthDegrees;
		if (isRadarMode && isResumed()) {
			renderParkingStateToViews();
		}
	}

	@Override
	public void onParkingLocationChanged(@Nullable CarParkingLocation location) {
		this.currentParkingLocation = location;
		if (getActivity() != null && isAdded()) {
			requireActivity().runOnUiThread(this::renderParkingStateToViews);
		}
	}
}
