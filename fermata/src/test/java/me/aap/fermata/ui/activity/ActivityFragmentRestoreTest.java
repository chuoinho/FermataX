package me.aap.fermata.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.ui.activity.ActivityBase;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.ui.fragment.ActivityFragment;

@RunWith(RobolectricTestRunner.class)
public class ActivityFragmentRestoreTest {

	@After
	public void tearDown() {
		Host.pendingDelegate = null;
	}

	@Test
	public void savedFragmentsDoNotOutrunAPendingDelegate() {
		Bundle saved = stateWithFragment();
		Host.pendingDelegate = new Promise<>();

		// Crashed here with "FutureSupplier is not done" when the process had been killed.
		Host host = Robolectric.buildActivity(Host.class).setup(saved).get();
		assertTrue(host.getSupportFragmentManager().getFragments().isEmpty());

		TestDelegate delegate = new TestDelegate(host);
		Host.pendingDelegate.complete(delegate);
		shadowOf(Looper.getMainLooper()).idle();
		assertNotNull("the delegate still gets the saved state", delegate.createState);
	}

	@Test
	public void savedFragmentsAreRestoredWhenTheDelegateIsReady() {
		Host host = Robolectric.buildActivity(Host.class).setup(stateWithFragment()).get();
		assertEquals(1, host.getSupportFragmentManager().getFragments().size());
	}

	private static Bundle stateWithFragment() {
		ActivityController<Host> first = Robolectric.buildActivity(Host.class).setup();
		first.get().getSupportFragmentManager().beginTransaction()
				.add(android.R.id.content, new DelegateFragment()).commitNow();
		Bundle saved = new Bundle();
		first.saveInstanceState(saved);
		first.pause().stop().destroy();
		return saved;
	}

	public static class Host extends ActivityBase {
		static Promise<ActivityDelegate> pendingDelegate;

		@Override
		protected FutureSupplier<? extends ActivityDelegate> createDelegate(AppActivity a) {
			return (pendingDelegate != null) ? pendingDelegate : Completed.completed(new TestDelegate(a));
		}

		@Override
		protected boolean canRestoreFragments() {
			return pendingDelegate == null;
		}
	}

	public static class DelegateFragment extends ActivityFragment {
		@Override
		public int getFragmentId() {
			return 1;
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			return new View(requireContext());
		}

		@Override
		public void onViewCreated(View view, Bundle state) {
			super.onViewCreated(view, state);
			assertNotNull(getActivityDelegate());
		}
	}

	private static class TestDelegate extends ActivityDelegate {
		Bundle createState;

		TestDelegate(AppActivity activity) {
			super(activity);
		}

		@Override
		protected void onActivityCreate(Bundle state) {
			super.onActivityCreate(state);
			createState = state;
		}

		@Override
		protected int getFrameContainerId() {
			return android.R.id.content;
		}

		@Override
		protected int getExitMsg() {
			return android.R.string.ok;
		}
	}
}
