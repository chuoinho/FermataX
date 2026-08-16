package me.aap.fermata.ui.view;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

/** Default Fermata top-bar mediator. Structure is installed here; semantics render in TopBarController. */
public final class TopBarMediator implements ToolBarView.Mediator.BackTitle {
	public static final ToolBarView.Mediator instance = new TopBarMediator();

	private TopBarMediator() {
	}

	@Override
	public void enable(ToolBarView toolBar, ActivityFragment fragment) {
		TopBarMediatorSupport.installBackTitle(toolBar, fragment, this);
		TopBarController.refresh(MainActivityDelegate.get(toolBar.getContext()), fragment);
	}

	@Override
	public void onActivityEvent(ToolBarView toolBar, ActivityDelegate activity, long event) {
		ActivityFragment fragment = activity.getActiveFragment();
		if (fragment != null) TopBarController.refresh((MainActivityDelegate) activity, fragment);
	}
}
