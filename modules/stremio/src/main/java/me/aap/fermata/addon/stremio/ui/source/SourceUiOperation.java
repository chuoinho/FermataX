package me.aap.fermata.addon.stremio.ui.source;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import me.aap.fermata.addon.stremio.lifecycle.StremioCall;

/** Cancellable operation returned by the runtime adapter. */
public interface SourceUiOperation extends StremioCall<SourceUiResult> {
	CompletableFuture<SourceUiResult> completion();

	void cancel();

	static SourceUiOperation of(CompletableFuture<SourceUiResult> completion,
			Runnable cancellation) {
		Objects.requireNonNull(completion, "completion");
		Objects.requireNonNull(cancellation, "cancellation");
		return new SourceUiOperation() {
			@Override
			public CompletableFuture<SourceUiResult> completion() {
				return completion;
			}

			@Override
			public void cancel() {
				cancellation.run();
			}

			@Override
			public String toString() {
				return "SourceUiOperation[pending=" + !completion.isDone() + ']';
			}
		};
	}
}
