package me.aap.fermata.auto;

import static me.aap.utils.async.Completed.completed;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import me.aap.fermata.auto.AutomotiveNavigationController.OpenResult;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.policy.RuntimeHostMode;
import me.aap.utils.async.FutureSupplier;

/** Per-source selection lifetime. Execution never consults the switch a second time. */
public final class OpenOnCarMediaRouting {
	private final AutomotiveNavigationController controller;
	private long generation;
	private long dispatchedGeneration = -1;

	public OpenOnCarMediaRouting(AutomotiveNavigationController controller) {
		this.controller = controller;
	}

	public Selection capture(RuntimeHostMode source, Cause cause, RuntimeHostMode activeOwner) {
		RuntimeHostMode target = cause != Cause.USER_SELECTION ? activeOwner :
				(source == RuntimeHostMode.PHONE && controller.getOpenOnCarMode().isEnabled() ?
						RuntimeHostMode.AA_PROJECTION : source);
		return new Selection(source, target, cause, controller.captureSourceToken(++generation));
	}

	public boolean isCurrent(Selection selection) {
		if (selection.token.sourceGeneration() != generation) return false;
		if (!selection.forwardToCar()) return true;
		OpenOnCarToken live = controller.captureSourceToken(generation);
		return controller.getOpenOnCarMode().isEnabled() &&
				live.connectionEpoch() == selection.token.connectionEpoch() &&
				live.registrationGeneration() == selection.token.registrationGeneration() &&
				live.modeRevision() == selection.token.modeRevision();
	}

	public FutureSupplier<OpenResult> dispatch(Selection selection, OpenOnCarKind kind,
			int addonId, Object payload, Supplier<FutureSupplier<OpenResult>> local) {
		return dispatch(selection, kind, addonId, payload, local, () -> true);
	}

	public FutureSupplier<OpenResult> dispatch(Selection selection, OpenOnCarKind kind,
			int addonId, Object payload, Supplier<FutureSupplier<OpenResult>> local, BooleanSupplier sourceCurrent) {
		if (!isCurrent(selection) || dispatchedGeneration == selection.token.sourceGeneration())
			return completed(OpenResult.CANCELLED);
		if (selection.forwardToCar() && controller.captureSourceToken(generation).requestId() !=
				selection.token.requestId()) return completed(OpenResult.CANCELLED);
		dispatchedGeneration = selection.token.sourceGeneration();
		if (!selection.forwardToCar()) return local.get();
		return controller.open(new OpenOnCarRequest(kind, addonId, payload, selection.token),
				() -> isCurrent(selection) && sourceCurrent.getAsBoolean());
	}

	public void invalidate() { generation++; }

	public enum Cause { USER_SELECTION, TRANSPORT, QUEUE_CONTINUATION }

	public record Selection(RuntimeHostMode source, RuntimeHostMode target, Cause cause,
			OpenOnCarToken token) {
		public boolean forwardToCar() {
			return cause == Cause.USER_SELECTION && source == RuntimeHostMode.PHONE &&
					target == RuntimeHostMode.AA_PROJECTION;
		}
	}

	/** Retains the presented wrapper, and therefore its queue context, until the normal resolver. */
	public record MediaItem(PlayableItem item, long position) {
		public MediaItem { java.util.Objects.requireNonNull(item); }
	}

	/** Mode/source/connection admission ends at commit; toggling OFF cannot stop an owner. */
	public static final class Admission {
		private final RuntimeHostMode target;
		private final BooleanSupplier current;
		private final java.util.function.Supplier<OpenOnCarToken> hostToken;
		private final OpenOnCarToken hostLease;
		private boolean committed;
		public Admission(RuntimeHostMode target, BooleanSupplier current) {
			this(target, current, () -> AutomotiveNavigationController.get().captureSourceToken(0));
		}
		Admission(RuntimeHostMode target, BooleanSupplier current,
				java.util.function.Supplier<OpenOnCarToken> hostToken) {
			this.target = target;
			this.current = current;
			this.hostToken = hostToken;
			hostLease = hostToken.get();
		}
		public RuntimeHostMode target() { return target; }
		public long hostRegistrationGeneration() { return hostLease.registrationGeneration(); }
		public boolean isCurrent() {
			OpenOnCarToken live = hostToken.get();
			boolean hostLeaseCurrent = target != RuntimeHostMode.AA_PROJECTION ||
					(live.connectionEpoch() == hostLease.connectionEpoch() &&
						live.registrationGeneration() == hostLease.registrationGeneration());
			return hostLeaseCurrent && (committed || current.getAsBoolean());
		}
		public boolean commit() { return committed = isCurrent(); }
		public boolean canAttach() {
			if (target != RuntimeHostMode.AA_PROJECTION) return true;
			var controller = AutomotiveNavigationController.get();
			var live = controller.captureSourceToken(0);
			return controller.getOpenOnCarMode().isAvailable() &&
					live.connectionEpoch() == hostLease.connectionEpoch() &&
					live.registrationGeneration() == hostLease.registrationGeneration();
		}
	}
}
