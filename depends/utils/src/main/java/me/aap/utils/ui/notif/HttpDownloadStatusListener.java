package me.aap.utils.ui.notif;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.DrawableRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import me.aap.utils.function.Function;
import me.aap.utils.net.http.HttpFileDownloader;
import me.aap.utils.net.http.HttpFileDownloader.Status;

import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.content.Context.NOTIFICATION_SERVICE;

/**
 * @author Andrey Pavlenko
 */
public class HttpDownloadStatusListener implements HttpFileDownloader.StatusListener {
	private static final AtomicInteger idCounter = new AtomicInteger();
	private final String channelId;
	private final Context context;
	private final NotificationManagerCompat mgr;
	private final NotificationCompat.Builder builder;
	private final int id = idCounter.incrementAndGet();
	private Function<Status, String> failureTitle;

	public HttpDownloadStatusListener(Context ctx) {
		this(ctx, "me.aap.utils.http.download", "HttpDownload");
	}

	public HttpDownloadStatusListener(Context ctx, String channelId, String channelName) {
		this.channelId = channelId;
		context = ctx.getApplicationContext();
		mgr = NotificationManagerCompat.from(ctx);
		builder = new NotificationCompat.Builder(ctx, channelId);
		builder.setChannelId(channelId);
		builder.setAutoCancel(true);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel nc = new NotificationChannel(channelId, channelName, IMPORTANCE_LOW);
			NotificationManager nmgr = (NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE);
			if (nmgr != null) nmgr.createNotificationChannel(nc);
		}
	}

	public void setSmallIcon(@DrawableRes int icon) {
		builder.setSmallIcon(icon);
	}

	public void setTitle(@Nonnull String title) {
		builder.setContentTitle(title);
	}

	public void setFailureTitle(Function<Status, String> title) {
		failureTitle = title;
	}

	@Override
	public void onProgress(Status status) {
		long total = status.getLength();

		if (total < 0) {
			builder.setProgress(0, 0, true);
		} else {
			int shift = progressShift(total);
			builder.setProgress((int) (total >>> shift), (int) (status.bytesDownloaded() >>> shift), false);
		}

		notifyIfPermitted();
	}

	@Override
	public void onSuccess(Status status) {
		mgr.cancel(channelId, id);
	}

	@Override
	public void onFailure(Status status) {
		if (failureTitle != null) builder.setContentTitle(failureTitle.apply(status));
		else builder.setContentTitle(context.getString(me.aap.utils.R.string.download_failed,
				status.getUrl()));
		notifyIfPermitted();
	}

	private void notifyIfPermitted() {
		if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) &&
				(ContextCompat.checkSelfPermission(context,
						Manifest.permission.POST_NOTIFICATIONS) != PERMISSION_GRANTED)) return;
		try {
			mgr.notify(channelId, id, builder.build());
		} catch (SecurityException ignored) {
			// Permission may be revoked between the check and the binder call; downloads continue.
		}
	}

	private static int progressShift(long total) {
		if (total <= Integer.MAX_VALUE) return 0;

		for (int shift = 1; ; shift++) {
			if ((total >> shift) <= Integer.MAX_VALUE) return shift;
		}
	}
}
