package me.app.fermatax.auto;

import static me.aap.utils.async.Completed.completed;

import android.os.Build;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.aap.fermata.FermataApplication;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

public class Su {
	private static volatile FutureSupplier<Su> su;
	private final ExecutorService executor;
	private final Process process;
	private final PrintWriter pw;

	private Su(ExecutorService executor, Process process, PrintWriter pw) {
		this.executor = executor;
		this.process = process;
		this.pw = pw;
	}

	public static FutureSupplier<Su> get() {
		var f = su;
		if (f != null) return f;
		synchronized (Su.class) {
			if ((f = su) != null) return f;
			var executor = Executors.newSingleThreadExecutor();
			var promise = new Promise<Su>();
			su = f = promise.onSuccess(instance -> Su.su = completed(instance))
					.onFailure(err -> {
						Log.e(err);
						executor.shutdown();
						Su.su = null;
					});
			executor.execute(() -> {
				try {
					int timeout = 30000;
					var pb = new ProcessBuilder("su", "-c", "ls");
					var p = pb.start();
					int ec;

					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
						if (p.waitFor(timeout, TimeUnit.MILLISECONDS)) ec = p.exitValue();
						else ec = 1;
					} else {
						FermataApplication.get().getHandler().postDelayed(p::destroy, timeout);
						ec = p.waitFor();
					}
					if (ec == 0) {
						var rootProc = new ProcessBuilder("su").start();
						var pw = new PrintWriter(rootProc.getOutputStream(), true);
						promise.complete(new Su(executor, rootProc, pw));
					} else {
						promise.completeExceptionally(new IOException("su not found or permission denied"));
						p.destroy();
					}
				} catch (Exception err) {
					promise.completeExceptionally(err);
				}
			});
			return f;
		}
	}

	public boolean isAlive() {
		return (process != null) && process.isAlive() && !pw.checkError();
	}

	public void exec(String cmd) {
		executor.execute(() -> {
			try {
				if (!isAlive()) {
					Log.w("Root shell process is no longer active. Dropping command: ", cmd);
					synchronized (Su.class) {
						Su.su = null;
					}
					return;
				}
				Log.d("executing: ", cmd);
				pw.println(cmd);
				pw.flush();
			} catch (Exception err) {
				Log.e(err, "Failed to execute command as root: ", cmd);
			}
		});
	}

	public void close() {
		executor.execute(() -> {
			try {
				pw.println("exit");
				pw.flush();
				pw.close();
				if (process != null) process.destroy();
			} catch (Exception ignored) {
			} finally {
				executor.shutdown();
				synchronized (Su.class) {
					Su.su = null;
				}
			}
		});
	}
}
