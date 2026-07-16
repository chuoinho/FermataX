package me.app.fermatax.auto;

import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_DPAD_UP_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP_RIGHT;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.ui.UiUtils.showAlert;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.OperationCanceledException;
import android.os.SystemClock;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView.OnEditorActionListener;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarUiController;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.FermataMediaServiceConnection;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.FermataActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.MediaItemListView;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.Function;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.TextChangedListener;

/**
 * @author Andrey Pavlenko
 */
public class MainCarActivity extends CarActivity implements FermataActivity {
	private static final DirectAppStartupCoordinator<FermataMediaServiceConnection> STARTUP =
			new DirectAppStartupCoordinator<>(FermataMediaServiceConnection::isConnected,
					FermataMediaServiceConnection::disconnect);
	@SuppressWarnings("unchecked")
	@NonNull
	private FutureSupplier<MainActivityDelegate> delegate =
			(FutureSupplier<MainActivityDelegate>) NO_DELEGATE;
	private CarEditText editText;
	private EditText activeInput;
	private TextWatcher textWatcher;
	private CarKeyboardOverlay keyboardOverlay;
	private CarTextInputSession textInputSession;
	private Promise<MainActivityDelegate> delegatePromise;
	private MainActivityDelegate createdDelegate;
	private Function<Context, ActivityDelegate> contextDelegate;
	private long startupGeneration;
	private boolean resumed;
	private boolean delegateResumed;
	private boolean destroyed;
	private boolean finishRequested;

	@NonNull
	@Override
	public FutureSupplier<MainActivityDelegate> getActivityDelegate() {
		return delegate;
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(MainActivityDelegate.attachBaseContext(base));
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		MainActivityDelegate.setTheme(this, true);
		super.onCreate(savedInstanceState);
		initCarActivity(this);
		int notifColor = FermataMediaServiceConnection.resolveNotificationColor(this);
		DirectAppStartupCoordinator.Startup<FermataMediaServiceConnection> startup =
				STARTUP.begin(() -> FermataMediaServiceConnection.connect(notifColor));
		startupGeneration = startup.getGeneration();
		Promise<MainActivityDelegate> promise = delegatePromise = new Promise<>();
		delegate = promise;
		startup.getConnection().main().onCompletion((connection, failure) ->
				completeStartup(savedInstanceState, startupGeneration, promise, connection, failure));
	}

	static void initCarActivity(CarActivity a) {
		a.setIgnoreConfigChanges(0xFFFFFFFF);
		CarUiController ctrl = a.getCarUiController();
		ctrl.getStatusBarController().hideAppHeader();
		ctrl.getMenuController().hideMenuButton();
	}

	private void completeStartup(Bundle state, long generation, Promise<MainActivityDelegate> promise,
			FermataMediaServiceConnection connection, Throwable failure) {
		if (failure != null) {
			STARTUP.uiFailed(generation);
			if (!destroyed && STARTUP.isCurrent(generation)) {
				showAlert(getContext(), String.valueOf(failure));
			}
			promise.completeExceptionally(failure);
			return;
		}
		if (destroyed || !STARTUP.beginUi(generation, connection)) {
			promise.cancel();
			return;
		}

		MainActivityDelegate d = null;
		try {
			MainActivityDelegate created =
					new MainActivityDelegate(this, connection.createBinder());
			d = created;
			createdDelegate = created;
			contextDelegate = ctx -> created;
			ActivityDelegate.setContextToDelegate(contextDelegate);
			// Views can synchronously resolve the delegate while onActivityCreate inflates the layout.
			delegate = completed(created);
			created.onActivityCreate(state);
			if (resumed) resumeDelegate(created);
			if (finishRequested) created.onActivityFinish();
			STARTUP.uiReady(generation);
			promise.complete(created);
		} catch (Throwable error) {
			Log.e(error, "Direct AA startup failed while attaching UI");
			if (d != null) {
				try {
					d.onActivityDestroy();
				} catch (Throwable cleanupError) {
					Log.e(cleanupError, "Failed to clean up incomplete AA startup");
				}
			}
			createdDelegate = null;
			delegateResumed = false;
			clearOwnedContextDelegate();
			delegate = (FutureSupplier<MainActivityDelegate>) NO_DELEGATE;
			STARTUP.uiFailed(generation);
			promise.completeExceptionally(error);
			if (!destroyed && STARTUP.isCurrent(generation)) {
				showAlert(getContext(), String.valueOf(error));
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		resumed = true;
		MainActivityDelegate d = createdDelegate;
		if (d != null) resumeDelegate(d);
	}

	@Override
	public void onPause() {
		super.onPause();
		resumed = false;
		MainActivityDelegate d = createdDelegate;
		if ((d == null) || !delegateResumed) return;
		delegateResumed = false;
		d.onActivityPause();
		MediaSessionCallback callback = d.getMediaSessionCallback();
		PlayableItem item = callback.getCurrentItem();
		if ((item != null) && item.isVideo() && callback.isPlaying()) callback.onPause();
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onDestroy() {
		stopInput();
		destroyed = true;
		super.onDestroy();
		MainActivityDelegate d = createdDelegate;
		createdDelegate = null;
		delegateResumed = false;
		if (d != null) d.onActivityDestroy();
		STARTUP.activityDestroyed(startupGeneration);
		clearOwnedContextDelegate();
		Promise<MainActivityDelegate> promise = delegatePromise;
		delegatePromise = null;
		if ((promise != null) && !promise.isDone()) promise.cancel();
		delegate = (FutureSupplier<MainActivityDelegate>) NO_DELEGATE;
	}

	private void resumeDelegate(MainActivityDelegate d) {
		if (delegateResumed) return;
		delegateResumed = true;
		d.onActivityResume();
	}

	private void clearOwnedContextDelegate() {
		Function<Context, ActivityDelegate> owner = contextDelegate;
		contextDelegate = null;
		if ((owner != null) && (ActivityDelegate.getContextToDelegate() == owner)) {
			ActivityDelegate.setContextToDelegate(null);
		}
	}

	@Override
	public void onConfigurationChanged(Configuration configuration) {
		Log.i("Configuration changed: ", configuration);
		super.onConfigurationChanged(configuration);
	}

	@Override
	@SuppressWarnings("unchecked")
	public View findViewById(int i) {
		return super.findViewById(i);
	}

	@NonNull
	@Override
	public FragmentManager getSupportFragmentManager() {
		return super.getSupportFragmentManager();
	}

	@Override
	public View getCurrentFocus() {
		return null;
	}

	public boolean isCarActivity() {
		return true;
	}

	@Override
	public void setRequestedOrientation(int requestedOrientation) {
	}

	public void recreate() {
	}

	public void finish() {
		finishRequested = true;
		MainActivityDelegate d = createdDelegate;
		if (d != null) d.onActivityFinish();
	}

	static FermataMediaServiceConnection takeServiceForShutdown() {
		return STARTUP.shutdown();
	}

	@Override
	public FutureSupplier<Intent> startActivityForResult(Supplier<Intent> intent) {
		return failed(new UnsupportedOperationException());
	}

	public FutureSupplier<int[]> checkPermissions(String... perms) {
		return failed(new UnsupportedOperationException());
	}

	@Override
	public Window getWindow() {
		return c();
	}

	@Override
	public EditText startInput(TextWatcher w) {
		cancelTextInputSession();
		if (editText == null) editText = new CarEditText(this);
		setActiveInput(editText, w);
		getActivityDelegate().onSuccess(a -> {
			if (a.getPrefs().getVoiceControlEnabledPref()) {
				a.startSpeechRecognizer(null, true).onCompletion((q, err) -> {
					stopInput();
					if (err instanceof OperationCanceledException) {
						setActiveInput(editText, w);
						if (w instanceof OnEditorActionListener)
							editText.setOnEditorActionListener((OnEditorActionListener) w);
						startCarInput(editText, false);
					} else if ((q != null) && !q.isEmpty()) {
						editText.setText(q.get(0));
						w.afterTextChanged(editText.getText());
					} else {
						stopInput();
					}
				});
			} else {
				startCarInput(editText, false);
			}
		});
		return editText;
	}

	@Override
	public EditText startInput(EditText target, boolean submitOnEnter, TextWatcher w) {
		cancelTextInputSession();
		if (!(target instanceof CarEditText input)) {
			return startInput(w);
		}
		setActiveInput(input, w);
		startCarInput(input, submitOnEnter);
		return input;
	}

	private void setActiveInput(EditText input, TextWatcher w) {
		if ((activeInput != null) && (textWatcher != null)) {
			activeInput.removeTextChangedListener(textWatcher);
			activeInput.setOnEditorActionListener(null);
		}

		activeInput = input;
		textWatcher = w;
		input.setOnEditorActionListener(null);
		input.addTextChangedListener(w);
	}

	private void startCarInput(CarEditText input, boolean submitOnEnter) {
		getWindow().getDecorView().post(() -> {
			if (input != activeInput) return;
			input.requestFocus();
			getKeyboardOverlay().show(input, submitOnEnter);
		});
	}

	private CarKeyboardOverlay getKeyboardOverlay() {
		if (keyboardOverlay == null) keyboardOverlay = new CarKeyboardOverlay(this);
		return keyboardOverlay;
	}

	@Override
	public FutureSupplier<String> requestTextInput(CharSequence title, CharSequence initialText,
			int inputType) {
		stopInput();
		CarEditText input = new CarEditText(this);
		input.setHint(title);
		input.setSingleLine(true);
		input.setInputType(inputType);
		input.setText(initialText == null ? "" : initialText);
		input.setSelection(input.getText().length());

		CarTextInputSession session = new CarTextInputSession();
		textInputSession = session;
		setActiveInput(input, (TextChangedListener) text -> {
		});
		input.setOnEditorActionListener((view, actionId, event) -> {
			if ((actionId != EditorInfo.IME_ACTION_DONE) &&
					(actionId != EditorInfo.IME_ACTION_GO) &&
					(actionId != EditorInfo.IME_ACTION_SEARCH)) return false;
			if (textInputSession == session) session.submit(input.getText());
			return true;
		});
		startCarInput(input, true);
		session.getResult().onCompletion((value, error) ->
				getWindow().getDecorView().post(() -> finishTextInputSession(session)));
		return session.getResult();
	}

	public void stopInput() {
		cancelTextInputSession();
		stopInputView();
	}

	private void stopInputView() {
		if (keyboardOverlay != null) keyboardOverlay.dismiss();
		if (activeInput != null) {
			if (textWatcher != null) activeInput.removeTextChangedListener(textWatcher);
			activeInput.setOnEditorActionListener(null);
		}

		activeInput = null;
		textWatcher = null;
		a().stopInput();
	}

	private void cancelTextInputSession() {
		CarTextInputSession session = textInputSession;
		if (session == null) return;
		textInputSession = null;
		if (!session.isDone()) session.cancel();
	}

	private void finishTextInputSession(CarTextInputSession session) {
		if (textInputSession != session) return;
		textInputSession = null;
		stopInputView();
	}

	public boolean isInputActive() {
		// The in-app keyboard owns projected text input. The legacy car input manager can
		// remain active after its surface is dismissed and would make the touch interceptor
		// consume every subsequent tap.
		return (keyboardOverlay != null) && keyboardOverlay.isShowing();
	}

	public EditText createEditText(Context ctx) {
		CarEditText et = new CarEditText(ctx);
		et.setOnClickListener(v -> {
			if ((keyboardOverlay == null) || !keyboardOverlay.isShowing()) {
				et.requestFocus();
				getKeyboardOverlay().show(et, false);
			}
		});
		return et;
	}

	@Override
	public boolean setTextInput(String text) {
		if (activeInput == null) return false;
		activeInput.setText(text);
		CarTextInputSession session = textInputSession;
		if (session == null) stopInput();
		else session.submit(text);
		return true;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
		Log.i(keyEvent);
		if ((keyboardOverlay != null) && keyboardOverlay.isShowing()) {
			if (keyCode == KEYCODE_BACK) return true;
		}
		MainActivityDelegate d = delegate.peek();
		if (d == null) return super.onKeyUp(keyCode, keyEvent);

		if (d.getPrefs().useDpadCursor(d)) {
			switch (keyCode) {
				case KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_LEFT,
						KEYCODE_DPAD_UP_RIGHT, KEYCODE_DPAD_DOWN_LEFT, KEYCODE_DPAD_DOWN_RIGHT -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if (c != null) c.delayedHide();
					return true;
				}
				case KEYCODE_BACK -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if ((c != null) && c.ignoreBack) {
						c.ignoreBack = false;
						return true;
					}
				}
				case KEYCODE_DPAD_CENTER -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if ((c != null) && c.isFocused()) return c.click();
				}
			}
		}

		return d.onKeyUp(keyCode, keyEvent, super::onKeyUp);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void onBackPressed() {
		getActivityDelegate().onSuccess(MainActivityDelegate::onBackPressed);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
		Log.i(keyEvent);
		if ((keyboardOverlay != null) && keyboardOverlay.onKeyDown(keyCode, keyEvent)) return true;
		MainActivityDelegate d = delegate.peek();
		if (d == null) return super.onKeyDown(keyCode, keyEvent);
		if (!d.getPrefs().useDpadCursor(d)) return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);

		float x = 0;
		float y = 0;
		View screen = null;
		Cursor cursor = null;

		switch (keyCode) {
			case KEYCODE_DPAD_UP -> {
				OverlayMenu m = d.getActiveMenu();
				if (m instanceof View v) {
					View f = v.focusSearch(View.FOCUS_UP);
					if (f != null) {
						f.requestFocus();
						return true;
					}
				}
				y = -1;
			}
			case KEYCODE_DPAD_DOWN -> {
				OverlayMenu m = d.getActiveMenu();
				if (m instanceof View v) {
					View f = v.focusSearch(View.FOCUS_DOWN);
					if (f != null) {
						f.requestFocus();
						return true;
					}
				}
				y = 1;
			}
			case KEYCODE_DPAD_LEFT -> x = -1;
			case KEYCODE_DPAD_RIGHT -> x = 1;
			case KEYCODE_DPAD_UP_LEFT -> {
				y = -1;
				x = -1;
			}
			case KEYCODE_DPAD_UP_RIGHT -> {
				y = -1;
				x = 1;
			}
			case KEYCODE_DPAD_DOWN_LEFT -> {
				y = 1;
				x = -1;
			}
			case KEYCODE_DPAD_DOWN_RIGHT -> {
				y = 1;
				x = 1;
			}
			case KEYCODE_BACK -> {
				screen = findViewById(R.id.main_activity);
				cursor = screen.findViewById(R.id.cursor);
				if ((cursor == null) || cursor.isFocused())
					return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);
				cursor.ignoreBack = true;
			}
			case KEYCODE_DPAD_CENTER -> {
				screen = findViewById(R.id.main_activity);
				cursor = screen.findViewById(R.id.cursor);
				if (cursor == null) return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);
			}
			default -> {return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);}
		}

		if (screen == null) {
			screen = findViewById(R.id.main_activity);
			cursor = screen.findViewById(R.id.cursor);
		}

		int w = screen.getWidth();
		int h = screen.getHeight();

		if (cursor == null) {
			cursor = new Cursor(d, (int) (Math.min(w, h) * 0.05f));
			((ViewGroup) screen).addView(cursor);
			cursor.show(w / 2f, h / 2f);
		} else if (!cursor.isVisible()) {
			cursor.show(cursor.getX(), cursor.getY());
		} else {
			int cursorSize = (int) (Math.min(w, h) * 0.05f);
			cursor.move(w, h, x, y, cursorSize / 3f, keyCode);
		}

		return true;
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent keyEvent) {
		MainActivityDelegate d = delegate.peek();
		return (d != null) ? d.onKeyLongPress(keyCode, keyEvent, super::onKeyLongPress) :
				super.onKeyLongPress(keyCode, keyEvent);
	}

	private static final class Cursor extends AppCompatImageView
			implements View.OnClickListener, View.OnLongClickListener {
		private final MainActivityDelegate activity;
		private Cancellable move = Cancellable.CANCELED;
		private Cancellable hide = Cancellable.CANCELED;
		private Cancellable resetAccel = Cancellable.CANCELED;
		boolean ignoreBack;
		int accel = 1;

		Cursor(MainActivityDelegate d, int size) {
			super(d.getContext());
			activity = d;
			setId(R.id.cursor);
			setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
			setLayoutParams(new ConstraintLayout.LayoutParams(size, size));
			setImageDrawable(AppCompatResources.getDrawable(d.getContext(), R.drawable.cursor));
			setOnClickListener(this);
			setOnLongClickListener(this);

			Drawable transparent = new ColorDrawable(Color.TRANSPARENT);
			StateListDrawable bg = new StateListDrawable();
			bg.addState(new int[]{android.R.attr.state_focused}, transparent);
			bg.addState(new int[]{}, transparent);
			setBackground(bg);
		}

		boolean isVisible() {
			return getVisibility() == VISIBLE;
		}

		@Override
		public void onClick(View v) {
			click();
		}

		boolean click() {
			delayedHide();
			float x = getX();
			float y = getY();
			OverlayMenu m = activity.getActiveMenu();
			if ((m instanceof ViewGroup v) && click(v, x - v.getX(), y - v.getY())) return true;
			click(screen(), x, y);
			return true;
		}

		private boolean click(ViewGroup parent, float cursorX, float cursorY) {
			for (int i = 0, n = parent.getChildCount(); i < n; i++) {
				View v = parent.getChildAt(i);
				if (v.getVisibility() != VISIBLE) continue;
				float x = cursorX - v.getX();
				if ((x > 0) && (x < v.getWidth())) {
					float y = cursorY - v.getY();
					if ((y >= 0) && (y < v.getHeight())) {
						if (v instanceof WebView) {
							touch(v, x, y, true);
							return true;
						}
						if (v instanceof VideoView) {
							touch(v, x, y, false);
							return true;
						}
						if (v.isClickable()) {
							v.performClick();
							v.requestFocus();
							activity.post(this::requestFocus);
							return true;
						}

						if ((v instanceof ViewGroup vg) && click(vg, x, y)) return true;
						touch(v, x, y, false);
						return true;
					}
				}
			}
			return false;
		}

		private void touch(View v, float x, float y, boolean clearFocus) {
			long time = SystemClock.uptimeMillis();
			MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
			if (clearFocus) {
				clearFocus();
				setVisibility(GONE);
			}
			v.dispatchTouchEvent(down);
			down.recycle();
			activity.postDelayed(() -> {
				MotionEvent up = MotionEvent.obtain(time, time + 100, MotionEvent.ACTION_UP, x, y, 0);
				v.dispatchTouchEvent(up);
				up.recycle();
				if (!clearFocus) activity.post(this::requestFocus);
			}, 100);
		}

		@Override
		public boolean onLongClick(View v) {
			delayedHide();
			longClick(screen(), getX(), getY());
			return true;
		}

		private void longClick(ViewGroup parent, float cursorX, float cursorY) {
			for (int i = 0, n = parent.getChildCount(); i < n; i++) {
				View v = parent.getChildAt(i);
				if (v.getVisibility() != VISIBLE) continue;
				float x = cursorX - v.getX();
				if ((x > 0) && (x < v.getWidth())) {
					float y = cursorY - v.getY();
					if ((y >= 0) && (y < v.getHeight())) {
						if (v.isLongClickable()) {
							v.performLongClick();
							activity.post(this::requestFocus);
							return;
						}
						if (v instanceof ViewGroup vg) longClick(vg, x, y);
						return;
					}
				}
			}
		}

		void delayedHide() {
			move.cancel();
			move = Cancellable.CANCELED;
			hide.cancel();
			hide = activity.postDelayed(() -> {
				hide = Cancellable.CANCELED;
				clearFocus();
				setVisibility(GONE);
				MediaItemListView.focusActive(activity.getContext(), null);
			}, 5000);
		}

		void show(float cursorX, float cursorY) {
			setVisibility(VISIBLE);
			animate().x(cursorX).y(cursorY).setDuration(0).start();
		}

		void move(int w, int h, float dx, float dy, float step, int keyCode) {
			move.cancel();
			move = activity.postDelayed(() -> move(w, h, dx, dy, step, keyCode), 50);

			float cursorX = getX() + (step * dx * accel);
			float cursorY = getY() + (step * dy * accel);
			cursorX = Math.max(0, Math.min(w - step, cursorX));
			cursorY = Math.max(0, Math.min(h - step, cursorY));

			if (resetAccel.cancel()) accel += 1;
			resetAccel = activity.postDelayed(() -> {
				resetAccel = Cancellable.CANCELED;
				accel = 1;
			}, 200);

			if ((keyCode == KEYCODE_DPAD_UP) && (getY() == 0)) {
				scroll(true);
			} else if ((keyCode == KEYCODE_DPAD_DOWN) && (getY() >= screen().getHeight() - getHeight())) {
				scroll(false);
			}

			if (!focusFb(screen(), cursorX, cursorY)) requestFocus();
			animate().x(cursorX).y(cursorY).setDuration(0).start();
		}

		private boolean focusFb(ViewGroup screen, float cursorX, float cursorY) {
			View fb = screen.findViewById(R.id.floating_button);
			float fbX = fb.getX();
			float fbY = fb.getY();
			if ((cursorX >= fbX) && (cursorX < fbX + fb.getWidth()) && (cursorY >= fbY) &&
					(cursorY < fbY + fb.getHeight())) {
				fb.requestFocus();
				return true;
			}
			return false;
		}

		private void scroll(boolean up) {
			ActivityFragment f = activity.getActiveFragment();
			if (f == null) return;
			View root = f.getView();
			if (root instanceof ViewGroup vg) scroll(up, vg);
		}

		private boolean scroll(boolean up, View v) {
			if (v instanceof RecyclerView rv) {
				LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
				if (lm == null) return false;
				int pos = lm.findFirstVisibleItemPosition();
				if (up) {
					if (pos > 0) lm.scrollToPositionWithOffset(pos - 1, 0);
				} else {
					if (pos < lm.getItemCount() - 1) lm.scrollToPositionWithOffset(pos + 1, 0);
				}
				return true;
			} else if (v instanceof WebView wv) {
				if (up) wv.pageUp(false);
				else wv.pageDown(false);
				return true;
			} else if (v instanceof ViewGroup vg) {
				for (int i = 0, n = vg.getChildCount(); i < n; i++) {
					if (scroll(up, vg.getChildAt(i))) return true;
				}
			}
			return false;
		}

		private ViewGroup screen() {
			return (ViewGroup) getParent();
		}
	}
}
