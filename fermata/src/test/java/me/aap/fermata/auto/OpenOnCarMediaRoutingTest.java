package me.aap.fermata.auto;

import static me.aap.fermata.auto.OpenOnCarMediaRouting.Cause.*;
import static me.aap.fermata.ui.policy.RuntimeHostMode.*;
import static me.aap.utils.async.Completed.completed;
import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import me.aap.fermata.auto.AutomotiveNavigationController.OpenResult;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

public class OpenOnCarMediaRoutingTest {
	@Test public void newerControllerRequestInvalidatesCapturedButNotYetDispatchedSelection() {
		Fixture f = new Fixture(); f.mode(true);
		var selection = f.routes.capture(PHONE, USER_SELECTION, PHONE);
		f.controller.open(77);
		assertEquals(OpenResult.CANCELLED, f.routes.dispatch(selection, OpenOnCarKind.MEDIA_ITEM,
				0, "item", () -> { throw new AssertionError(); }).peek());
		assertEquals(0, f.car.get());
	}
	@Test public void ownerTransportDoesNotUseCarBridgeAndOffCaptureRemainsPhone() {
		Fixture f = new Fixture();
		var off = f.routes.capture(PHONE, USER_SELECTION, AA_PROJECTION);
		f.mode(true);
		AtomicInteger local = new AtomicInteger();
		assertEquals(OpenResult.OPENED, f.routes.dispatch(off, OpenOnCarKind.MEDIA_ITEM, 0, "item",
				() -> { local.incrementAndGet(); return completed(OpenResult.OPENED); }).peek());
		var transport = f.routes.capture(PHONE, TRANSPORT, AA_PROJECTION);
		f.routes.dispatch(transport, OpenOnCarKind.MEDIA_ITEM, 0, "item",
				() -> { local.incrementAndGet(); return completed(OpenResult.OPENED); });
		assertEquals(2, local.get()); assertEquals(0, f.car.get());
	}
	@Test public void hostReplacementCancelsCapturedCarSelection() {
		Fixture f = new Fixture(); f.mode(true);
		var selection = f.routes.capture(PHONE, USER_SELECTION, PHONE);
		f.controller.register((id, guard) -> completed(OpenResult.OPENED));
		assertEquals(OpenResult.CANCELLED, f.routes.dispatch(selection, OpenOnCarKind.MEDIA_ITEM,
				0, "item", () -> { throw new AssertionError(); }).peek());
	}
	@Test public void duplicateDispatchOfCapturedSelectionIsCancelled() {
		Fixture f = new Fixture(); f.mode(true);
		var selection = f.routes.capture(PHONE, USER_SELECTION, PHONE);
		f.routes.dispatch(selection, OpenOnCarKind.MEDIA_ITEM, 0, "item", () -> { throw new AssertionError(); });
		assertEquals(OpenResult.CANCELLED, f.routes.dispatch(selection, OpenOnCarKind.MEDIA_ITEM,
				0, "item", () -> { throw new AssertionError(); }).peek());
		assertEquals(1, f.car.get());
	}
	@Test public void offSelectsPhoneEvenWithReadyCar() {
		Fixture f = new Fixture();
		assertEquals(PHONE, f.routes.capture(PHONE, USER_SELECTION, AA_PROJECTION).target());
	}
	@Test public void onRoutesOnceWithoutRunningPhonePreparation() {
		Fixture f = new Fixture(); f.mode(true);
		var selection = f.routes.capture(PHONE, USER_SELECTION, PHONE);
		assertEquals(AA_PROJECTION, selection.target());
		AtomicInteger phone = new AtomicInteger();
		assertEquals(OpenResult.OPENED, f.routes.dispatch(selection, OpenOnCarKind.MEDIA_ITEM,
				0, "typed fixture", () -> { phone.incrementAndGet(); return completed(OpenResult.OPENED); }).peek());
		assertEquals(0, phone.get()); assertEquals(1, f.car.get());
	}
	@Test public void capturedCarDoesNotFallbackAfterDisconnectOrReplacement() {
		Fixture f = new Fixture(); f.mode(true);
		var selection = f.routes.capture(PHONE, USER_SELECTION, PHONE);
		f.connection.connectionChanged(false);
		AtomicInteger phone = new AtomicInteger();
		OpenResult result = f.routes.dispatch(selection, OpenOnCarKind.MEDIA_ITEM, 0, "item",
				() -> { phone.incrementAndGet(); return completed(OpenResult.OPENED); }).peek();
		assertTrue(result == OpenResult.NOT_READY || result == OpenResult.CANCELLED);
		assertEquals(0, phone.get()); assertEquals(0, f.car.get());
	}
	@Test public void transportAndQueueKeepActiveOwnerRegardlessOfSwitch() {
		Fixture f = new Fixture();
		for (boolean enabled : new boolean[]{false, true}) {
			f.mode(enabled);
			assertEquals(AA_PROJECTION, f.routes.capture(PHONE, TRANSPORT, AA_PROJECTION).target());
			assertEquals(PHONE, f.routes.capture(AA_PROJECTION, QUEUE_CONTINUATION, PHONE).target());
		}
	}
	@Test public void newSelectionInvalidatesOldSurfaceWorkAndSourceDetachCancelsCar() {
		Fixture f = new Fixture();
		var first = f.routes.capture(PHONE, USER_SELECTION, PHONE);
		f.routes.capture(PHONE, USER_SELECTION, PHONE);
		assertFalse(f.routes.isCurrent(first));
		f.mode(true);
		AtomicReference<BooleanSupplier> guard = new AtomicReference<>();
		Promise<OpenResult> pending = new Promise<>();
		f.controller.register(new AutomotiveNavigationController.Navigator() {
			public FutureSupplier<OpenResult> open(int id, BooleanSupplier current) { return pending; }
			public FutureSupplier<OpenResult> open(OpenOnCarRequest request, BooleanSupplier current) {
				guard.set(current); return pending;
			}
		});
		var selection = f.routes.capture(PHONE, USER_SELECTION, PHONE);
		var result = f.routes.dispatch(selection, OpenOnCarKind.MEDIA_ITEM, 0, "item",
				() -> { fail("phone fallback"); return null; });
		f.routes.invalidate();
		assertFalse(guard.get().getAsBoolean()); pending.complete(OpenResult.OPENED);
		assertEquals(OpenResult.CANCELLED, result.peek());
	}
	@Test public void admissionChecksLossUntilCommitButOffDoesNotStopCommittedPlayback() {
		AtomicBoolean current = new AtomicBoolean(true);
		var admission = new OpenOnCarMediaRouting.Admission(AA_PROJECTION, current::get);
		current.set(false); assertFalse(admission.commit()); assertFalse(admission.isCurrent());
		current.set(true); assertTrue(admission.commit());
		current.set(false); assertTrue(admission.isCurrent());
	}
	private static final class Fixture {
		final AutomotiveConnectionState connection = new AutomotiveConnectionState();
		final AutomotiveNavigationController controller = new AutomotiveNavigationController(connection);
		final OpenOnCarMediaRouting routes = new OpenOnCarMediaRouting(controller);
		final AtomicInteger car = new AtomicInteger();
		Fixture() {
			connection.connectionChanged(true);
			controller.register(new AutomotiveNavigationController.Navigator() {
				public FutureSupplier<OpenResult> open(int id, BooleanSupplier guard) { return completed(OpenResult.NOT_READY); }
				public FutureSupplier<OpenResult> open(OpenOnCarRequest request, BooleanSupplier guard) {
					car.incrementAndGet(); return completed(guard.getAsBoolean() ? OpenResult.OPENED : OpenResult.CANCELLED);
				}
			});
		}
		void mode(boolean enabled) { controller.getOpenOnCarMode().setEnabled(enabled); }
	}
}
