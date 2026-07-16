package me.aap.utils.ui.activity;

import static me.aap.utils.async.Completed.failed;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.Theme;
import android.os.Bundle;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.EditText;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.textfield.TextInputEditText;

import javax.annotation.Nonnull;

import me.aap.utils.R;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.ui.view.DialogBuilder;

/**
 * @author Andrey Pavlenko
 */
public interface AppActivity {
	FutureSupplier<? extends ActivityDelegate> NO_DELEGATE = failed(new ActivityDestroyedException());

	@Nonnull
	FutureSupplier<? extends ActivityDelegate> getActivityDelegate();

	Theme getTheme();

	void setTheme(int resid);

	Window getWindow();

	View getCurrentFocus();

	@NonNull
	FragmentManager getSupportFragmentManager();

	void setContentView(@LayoutRes int layoutResID);

	<T extends View> T findViewById(@IdRes int id);

	void recreate();

	void finish();

	default boolean isFinishing() {
		return false;
	}

	default boolean isDestroyed() {
		return false;
	}

	default void startActivity(Intent intent) {
		startActivity(intent, null);
	}

	void startActivity(Intent intent, @Nullable Bundle options);

	FutureSupplier<Intent> startActivityForResult(Supplier<Intent> intent);

	FutureSupplier<int[]> checkPermissions(String... perms);

	default Context getContext() {
		return (Context) this;
	}

	default EditText createEditText(Context ctx) {
		return new TextInputEditText(ctx, null, androidx.appcompat.R.attr.editTextStyle);
	}

	/**
	 * Lets projected activities own the complete text-input surface. Returning {@code null}
	 * keeps the regular dialog/IME path.
	 */
	@Nullable
	default FutureSupplier<String> requestTextInput(CharSequence title, CharSequence initialText,
			int inputType) {
		return null;
	}

	@Nullable
	default EditText startInput(TextWatcher w) {
		return null;
	}

	@Nullable
	default EditText startInput(EditText target, boolean submitOnEnter, TextWatcher w) {
		return startInput(w);
	}

	default void stopInput() {
	}

	default boolean isInputActive() {
		return false;
	}

	default DialogBuilder createDialogBuilder(Context ctx) {
		return DialogBuilder.create(ctx);
	}

	Intent getIntent();
}
