package me.aap.fermata.addon.web.yt;

import androidx.annotation.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.AutomotiveRuntimeGate;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;

/** Selects the live YouTube WebView host without owning MediaSession playback state. */
final class YoutubeRuntime {
	private final YoutubeAddon addon;
	private final Map<YoutubeWebView, Host> hosts = new IdentityHashMap<>();
	private long hostGeneration;
	private YoutubeSessionEngine session;
	private final YoutubeAutoSessionTracker autoSessions = new YoutubeAutoSessionTracker();

	YoutubeRuntime(YoutubeAddon addon) {
		this.addon = addon;
	}

	void registerHost(YoutubeWebView web, YoutubeMediaEngine engine,
			MainActivityDelegate activity) {
		hosts.put(web, new Host(++hostGeneration, web, engine, activity));
		YoutubeSessionEngine current = session;
		if (current != null) attachPreferred(current);
	}

	void unregisterHost(YoutubeWebView web, YoutubeMediaEngine engine) {
		Host host = hosts.get(web);
		if ((host == null) || (host.engine != engine)) return;
		hosts.remove(web);
		YoutubeSessionEngine current = session;
		if ((current != null) && current.owns(engine)) current.onDelegateDestroyed(engine);
	}

	YoutubeSessionEngine sessionEngine(@Nullable me.aap.fermata.media.engine.MediaEngine current,
			MediaSessionCallback callback, YoutubeItem descriptor) {
		if ((current instanceof YoutubeSessionEngine stable) && stable.belongsTo(addon)) {
			session = stable;
			return stable;
		}
		YoutubeSessionEngine created = new YoutubeSessionEngine(addon, this, callback, descriptor);
		if ((current instanceof YoutubeMediaEngine webEngine) && webEngine.belongsTo(addon)) {
			created.attach(webEngine);
		}
		session = created;
		return created;
	}

	boolean claimBrowserPlayback(YoutubeMediaEngine engine,
			MediaSessionCallback callback, YoutubePlaybackActivation activation) {
		YoutubeSessionEngine current = session;
		if ((current == null) || !current.attach(engine)) {
			current = new YoutubeSessionEngine(addon, this, callback, null);
			if (!current.attach(engine)) return false;
			session = current;
		}
		return current.activate(activation);
	}

	void requestHost(YoutubeSessionEngine requester) {
		if ((session != null) && (session != requester)) return;
		session = requester;
		if (attachPreferred(requester)) return;
		MainActivityDelegate activity = addon.currentPlaybackActivity();
		if (activity == null) return;
		ActivityFragment fragment = activity.showFragment(addon.getAddonId());
		if (fragment instanceof YoutubeFragment youtube) {
			YoutubeWebView web = youtube.getWebView();
			if (web != null) registerHost(web, web.getMediaEngine(), activity);
		}
	}

	void release(YoutubeSessionEngine released) {
		if (session == released) session = null;
	}

	YoutubeAddon.SessionReturnAction consumeEntry(MainActivityDelegate activity,
			boolean explicitTarget, boolean playbackActive) {
		if (!activity.isCarActivityNotMirror()) return null;
		long generation = AutomotiveRuntimeGate.currentGeneration();
		return (autoSessions.consume(activity, generation, explicitTarget, playbackActive) ==
				YoutubeAutoSessionTracker.Decision.RESET_HOME) ?
				YoutubeAddon.SessionReturnAction.RESET_HOME : YoutubeAddon.SessionReturnAction.KEEP;
	}

	private boolean attachPreferred(YoutubeSessionEngine target) {
		Host fallback = null;
		for (Host host : hosts.values()) {
			if (!host.web.isAttachedToWindow()) continue;
			if (addon.isPreferredPlaybackActivity(host.activity)) return target.attach(host.engine);
			if (fallback == null) fallback = host;
		}
		return (fallback != null) && target.attach(fallback.engine);
	}

	private record Host(long generation, YoutubeWebView web, YoutubeMediaEngine engine,
			MainActivityDelegate activity) {
	}
}
