package me.aap.fermata.addon.stremio.ui.source;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import me.aap.fermata.addon.stremio.R;
import me.aap.fermata.addon.stremio.ui.source.SourceUiController.EditorRequest;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.GenericDialogFragment;
import me.aap.utils.async.FutureSupplier;

/** AA-safe source editor hosted by Fermata's in-app dialog Fragment. */
public final class StremioSourceEditorDialog {
	private StremioSourceEditorDialog() {
	}

	public static boolean show(MainActivityDelegate activity, EditorRequest request,
			Consumer<SourceUiDraft> submit) {
		Objects.requireNonNull(activity, "activity");
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(submit, "submit");
		if (!(activity.showFragment(me.aap.utils.R.id.generic_dialog_fragment)
				instanceof GenericDialogFragment fragment)) return false;

		View form = LayoutInflater.from(activity.getContext())
				.inflate(R.layout.stremio_source_dialog, null, false);
		EditText url = form.findViewById(R.id.stremio_source_url);
		EditText token = form.findViewById(R.id.stremio_source_token);
		CheckBox allowHttp = form.findViewById(R.id.stremio_source_allow_http);
		CheckBox allowLan = form.findViewById(R.id.stremio_source_allow_lan);
		TextView error = form.findViewById(R.id.stremio_source_form_error);
		ProjectedFieldEditor projected = new ProjectedFieldEditor(activity);
		SourceUiDraft initial = request.initialDraft();
		url.setText(initial.transportUrl());
		token.setText(initial.configurationToken());
		allowHttp.setChecked(initial.consent().allowCleartext());
		allowLan.setChecked(initial.consent().allowLan());

		Runnable changed = () -> {
			SourceUiError validation = SourceFormValidator.validate(draft(
					url, token, allowHttp, allowLan));
			if (validation == SourceUiError.NONE) {
				error.setVisibility(GONE);
			} else {
				error.setText(StremioSourceUiView.errorText(activity.getContext(), validation));
				error.setVisibility(VISIBLE);
			}
			activity.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
		};
		TextWatcher watcher = new SimpleTextWatcher(changed);
		url.addTextChangedListener(watcher);
		token.addTextChangedListener(watcher);
		allowHttp.setOnCheckedChangeListener((button, checked) -> changed.run());
		allowLan.setOnCheckedChangeListener((button, checked) -> changed.run());
		Runnable submitForm = () -> {
			if (SourceFormValidator.validate(draft(url, token, allowHttp, allowLan)) !=
					SourceUiError.NONE) return;
			InputMethodManager keyboard = activity.getContext()
					.getSystemService(InputMethodManager.class);
			if (keyboard != null) keyboard.hideSoftInputFromWindow(token.getWindowToken(), 0);
			View ok = activity.getToolBar().findViewById(me.aap.utils.R.id.file_picker_ok);
			if (ok != null) ok.performClick();
		};
		Runnable openToken = () -> {
			token.requestFocus();
			token.post(() -> projected.open(token, R.string.stremio_source_token_hint,
					submitForm));
		};
		projected.bind(url, R.string.stremio_source_url_hint, openToken);
		projected.bind(token, R.string.stremio_source_token_hint, submitForm);

		fragment.setTitle(activity.getString(request.isEdit() ?
				R.string.stremio_source_edit : R.string.stremio_source_add));
		fragment.setContentProvider(group -> {
			form.setLayoutParams(new RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT));
			group.addView(form);
			projected.openWhenReady(url, R.string.stremio_source_url_hint, openToken,
					() -> {
						if ((activity.getActiveFragment() != fragment) ||
								!url.isAttachedToWindow() || !url.isShown()) return false;
						url.requestFocus();
						url.setSelection(0, url.length());
						return true;
					});
		});
		fragment.setDialogValidator(() -> SourceFormValidator.validate(
				draft(url, token, allowHttp, allowLan)) == SourceUiError.NONE);
		fragment.setDialogConsumer(ok -> {
			projected.close();
			url.removeTextChangedListener(watcher);
			token.removeTextChangedListener(watcher);
			if (ok) submit.accept(draft(url, token, allowHttp, allowLan));
		});
		fragment.setBackHandler(() -> false);
		configureKeyboardFlow(url, token, submitForm);
		changed.run();
		return true;
	}

	static final class ProjectedFieldEditor implements AutoCloseable {
		private final BiFunction<EditText, Integer, FutureSupplier<String>> requestInput;
		private final Consumer<EditText> showFallbackInput;
		private final Runnable stopInput;
		private FutureSupplier<String> active;
		private long generation;
		private boolean closed;

		private ProjectedFieldEditor(MainActivityDelegate activity) {
			this((field, titleResource) -> activity.getAppActivity().requestTextInput(
						activity.getString(titleResource), field.getText(), field.getInputType()),
					field -> {
						InputMethodManager keyboard = activity.getContext()
								.getSystemService(InputMethodManager.class);
						if (keyboard != null) keyboard.showSoftInput(field,
								InputMethodManager.SHOW_IMPLICIT);
					},
					() -> activity.getAppActivity().stopInput());
		}

		ProjectedFieldEditor(
				BiFunction<EditText, Integer, FutureSupplier<String>> requestInput,
				Consumer<EditText> showFallbackInput, Runnable stopInput) {
			this.requestInput = Objects.requireNonNull(requestInput, "requestInput");
			this.showFallbackInput = Objects.requireNonNull(showFallbackInput,
					"showFallbackInput");
			this.stopInput = Objects.requireNonNull(stopInput, "stopInput");
		}

		void bind(EditText field, int titleResource, Runnable accepted) {
			field.setOnClickListener(view -> open(field, titleResource, accepted));
		}

		void openWhenReady(EditText field, int titleResource, Runnable accepted,
				BooleanSupplier ready) {
			long scheduledGeneration = generation;
			field.post(new Runnable() {
				private int attempts;

				@Override
				public void run() {
					if (closed || (active != null) || (generation != scheduledGeneration)) return;
					if (ready.getAsBoolean()) {
						open(field, titleResource, accepted);
					} else if (++attempts < 20) {
						field.postDelayed(this, 50);
					}
				}
			});
		}

		private void open(EditText field, int titleResource, Runnable accepted) {
			if (closed || (active != null)) return;
			FutureSupplier<String> direct = requestInput.apply(field, titleResource);
			if (direct == null) {
				showFallbackInput.accept(field);
				return;
			}
			long expectedGeneration = ++generation;
			active = direct;
			direct.onSuccess(text -> {
				if (closed || (active != direct) || (generation != expectedGeneration)) return;
				active = null;
				field.setText(text == null ? "" : text);
				field.setSelection(field.length());
				accepted.run();
			}).onFailure(error -> {
				if (!closed && (active == direct) && (generation == expectedGeneration)) {
					active = null;
				}
			});
		}

		@Override
		public void close() {
			if (closed) return;
			closed = true;
			generation++;
			FutureSupplier<String> current = active;
			active = null;
			if (current != null) current.cancel();
			stopInput.run();
		}
	}

	static void configureKeyboardFlow(EditText url, EditText token, Runnable done) {
		url.setImeOptions(EditorInfo.IME_ACTION_NEXT);
		token.setImeOptions(EditorInfo.IME_ACTION_DONE);
		url.setOnEditorActionListener((view, action, event) -> {
			if (action != EditorInfo.IME_ACTION_NEXT) return false;
			token.requestFocus();
			return true;
		});
		token.setOnEditorActionListener((view, action, event) -> {
			if (action != EditorInfo.IME_ACTION_DONE) return false;
			done.run();
			return true;
		});
	}

	private static SourceUiDraft draft(EditText url, EditText token,
			CheckBox allowHttp, CheckBox allowLan) {
		return new SourceUiDraft(url.getText().toString().trim(),
				token.getText().toString(),
				new SourceUiConsent(allowHttp.isChecked(), allowLan.isChecked()));
	}

	private static final class SimpleTextWatcher implements TextWatcher {
		private final Runnable changed;

		private SimpleTextWatcher(Runnable changed) {
			this.changed = changed;
		}

		@Override
		public void beforeTextChanged(CharSequence text, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence text, int start, int before, int count) {
		}

		@Override
		public void afterTextChanged(Editable text) {
			changed.run();
		}
	}
}
