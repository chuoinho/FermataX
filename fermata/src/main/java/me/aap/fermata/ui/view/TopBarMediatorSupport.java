package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.LEFT;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.RIGHT;

import android.widget.EditText;
import android.widget.TextView;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

/**
 * Installs the standard Fermata top-bar view structure without resolving route/playback semantics.
 * Title text and route Back visibility are rendered exclusively by {@link TopBarController}.
 */
public final class TopBarMediatorSupport {
	private TopBarMediatorSupport() {
	}

	/** Installs the canonical Back affordance for custom top bars that do not render a title. */
	public static void installBackButton(ToolBarView toolBar, ToolBarView.Mediator mediator) {
		var back = mediator.addButton(toolBar, me.aap.utils.R.drawable.back,
				v -> MainActivityDelegate.get(v.getContext()).onBackPressed(),
				me.aap.utils.R.id.tool_bar_back_button, getBackButtonSide(toolBar));
		// Route visibility is rendered only by TopBarController. Avoid a one-frame visible default.
		back.setVisibility(GONE);
	}

	/** PHONE and automotive hosts share Back semantics; only physical placement may differ. */
	public static int getBackButtonSide(ToolBarView toolBar) {
		if (BuildConfig.AUTO && MainActivityDelegate.get(toolBar.getContext()).getNavBar().isRight())
			return RIGHT;
		return LEFT;
	}

	public static void installBackTitle(ToolBarView toolBar, ActivityFragment fragment,
			ToolBarView.Mediator.BackTitle mediator) {
		toolBar.setVisibility(mediator.getVisibility(toolBar, fragment));
		TextView title = mediator.createTitleText(toolBar);
		mediator.addView(toolBar, title, mediator.getTitleId(), LEFT);
		if (mediator.backOnTitleClick()) title.setOnClickListener(mediator);

		var back = mediator.createBackButton(toolBar);
		mediator.addView(toolBar, back, mediator.getBackButtonId(), getBackButtonSide(toolBar));
	}

	public static void installBackTitleFilter(ToolBarView toolBar, ActivityFragment fragment,
			ToolBarView.Mediator.BackTitleFilter mediator) {
		EditText filter = mediator.createFilter(toolBar);
		filter.setVisibility(GONE);
		mediator.addView(toolBar, filter, mediator.getFilterId(), LEFT);
		installBackTitle(toolBar, fragment, mediator);

		var filterButton = mediator.createFilterButton(toolBar);
		mediator.addView(toolBar, filterButton, mediator.getFilterButtonId());
		mediator.setFilterVisibility(toolBar, false);
	}
}
