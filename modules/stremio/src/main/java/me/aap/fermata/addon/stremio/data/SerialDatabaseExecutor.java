package me.aap.fermata.addon.stremio.data;

import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import me.aap.fermata.diagnostics.DiagnosticIds;
import me.aap.fermata.diagnostics.DiagnosticOperation;
import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;

final class SerialDatabaseExecutor {
	private final Object lock = new Object();
	private final ExecutorService executor;
	private final CompletableFuture<Void> ready = new CompletableFuture<>();
	private final CompletableFuture<Void> closed = new CompletableFuture<>();
	private boolean accepting = true;
	private SQLiteDatabase database;
	private Throwable openFailure;

	SerialDatabaseExecutor(File file) {
		this(file, StremioSchema.CURRENT_VERSION, StremioSchema.migrations());
	}

	SerialDatabaseExecutor(File file, int targetVersion,
			List<StremioSchema.Migration> migrations) {
		executor = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "FermataX-Stremio-DB");
			thread.setDaemon(true);
			return thread;
		});
		executor.execute(() -> {
			DiagnosticOperation diagnostics = beginOpenDiagnostics(targetVersion);
			try {
				database = StremioDatabase.open(file, targetVersion, migrations);
				completeDiagnostics(diagnostics);
				ready.complete(null);
			} catch (Throwable error) {
				failDiagnostics(diagnostics, error);
				openFailure = error;
				ready.completeExceptionally(error);
				synchronized (lock) {
					if (accepting) {
						accepting = false;
						executor.execute(this::finishClose);
					}
				}
			}
		});
	}

	CompletableFuture<Void> ready() {
		return ready;
	}

	<T> CompletableFuture<T> submit(DatabaseOperation<T> operation) {
		CompletableFuture<T> result = new CompletableFuture<>();
		synchronized (lock) {
			if (!accepting) {
				result.completeExceptionally(new RejectedExecutionException(
						"Stremio repository is closing or closed"));
				return result;
			}
			executor.execute(() -> run(operation, result));
		}
		return result;
	}

	CompletableFuture<Void> closeAsync() {
		synchronized (lock) {
			if (!accepting) return closed;
			accepting = false;
			executor.execute(this::finishClose);
			return closed;
		}
	}

	private void finishClose() {
		try {
			if (database != null) database.close();
			closed.complete(null);
		} catch (Throwable error) {
			closed.completeExceptionally(error);
		} finally {
			executor.shutdown();
		}
	}

	private <T> void run(DatabaseOperation<T> operation, CompletableFuture<T> result) {
		if (openFailure != null) {
			result.completeExceptionally(openFailure);
			return;
		}
		try {
			result.complete(operation.run(database));
		} catch (Throwable error) {
			recordOperationFailure(error);
			result.completeExceptionally(error);
		}
	}

	private static DiagnosticOperation beginOpenDiagnostics(int targetVersion) {
		try {
			return AndroidDiagnosticsRuntime.get().begin("database", "stremio_database_open",
					java.util.Map.of("revision", targetVersion));
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static void completeDiagnostics(DiagnosticOperation diagnostics) {
		if (diagnostics == null) return;
		try {
			diagnostics.complete(java.util.Map.of("status", "completed"));
		} catch (Throwable ignored) {
		}
	}

	private static void failDiagnostics(DiagnosticOperation diagnostics, Throwable error) {
		if (diagnostics == null) return;
		try {
			diagnostics.fail(error, java.util.Map.of("status", "failed"));
		} catch (Throwable ignored) {
		}
	}

	private static void recordOperationFailure(Throwable error) {
		try {
			AndroidDiagnosticsRuntime.get().recordEssential("database", "database_operation_failed",
					DiagnosticPriority.ERROR, DiagnosticIds.next(),
					java.util.Map.of("provider_class", "stremio", "error", error));
		} catch (Throwable ignored) {
		}
	}

	@FunctionalInterface
	interface DatabaseOperation<T> {
		T run(SQLiteDatabase database) throws Exception;
	}
}
