package me.aap.fermata.addon.stremio.ui;

import android.content.Context;

import java.util.function.Consumer;

import me.aap.fermata.addon.stremio.R;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.UiUtils;

/** Owns one keyboard request and a pre-initialization query without owning fragment lifecycle. */
public final class StremioSearchController {
	private FutureSupplier<String> input;
	private String pendingQuery;

	public void showResults(String query, boolean ready, Consumer<String> navigate) {
		if ((query == null) || (query = query.trim()).isEmpty()) return;
		if (!ready) {
			pendingQuery = query;
			return;
		}
		navigate.accept(query);
	}

	public void request(Context context, Consumer<String> navigate) {
		FutureSupplier<String> old = input;
		if (old != null) old.cancel();
		FutureSupplier<String> current = UiUtils.queryText(context, R.string.stremio_search,
				me.aap.fermata.R.drawable.search).main();
		input = current;
		current.onSuccess(query -> {
			if (input != current) return;
			input = null;
			navigate.accept(query);
		}).onFailure(error -> {
			if (input == current) input = null;
		});
	}

	public String takePendingQuery() {
		String query = pendingQuery;
		pendingQuery = null;
		return query;
	}

	public void clearView() {
		FutureSupplier<String> current = input;
		input = null;
		if (current != null) current.cancel();
	}
}
