package me.aap.fermata.media.service;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.net.NetServer;

final class PlayableItemPreparer {
	private PlayableItemPreparer() {
	}

	static FutureSupplier<PlayableItem> prepare(PlayableItem target,
			BooleanSupplier terminal, Supplier<FutureSupplier<NetServer>> netServer,
			Consumer<PlayableItem> missingLocal) {
		if (terminal.getAsBoolean() || (target == null)) return completedNull();
		if (target.isPlaybackTransportCommand()) return completed(target).main();
		if (target.getResource().isLocalFile()) {
			return target.getResource().exists().then(exists -> {
				if (exists) return prepareAvailable(target, terminal, netServer);
				missingLocal.accept(target);
				return completedNull();
			}).main();
		}
		return prepareAvailable(target, terminal, netServer);
	}

	private static FutureSupplier<PlayableItem> prepareAvailable(PlayableItem target,
			BooleanSupplier terminal, Supplier<FutureSupplier<NetServer>> netServer) {
		if (terminal.getAsBoolean()) return completedNull();
		FutureSupplier<Long> duration = target.getDuration();
		if (target.isNetResource()) {
			FutureSupplier<NetServer> start = netServer.get();
			if (!start.isDone()) return start.and(duration, (server, value) -> {})
					.map(value -> target).main();
		}
		if (!duration.isDone()) {
			return duration.map(value -> target).timeout(5000, () -> target).main();
		}
		return completed(target).main();
	}
}
