package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.LEFT;

import android.widget.EditText;
import android.widget.TextView;

import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

/**
 * Installs the standard Fermata top-bar view structure without resolving route/playback semantics.
 * Title text and route Back visibility are rendered exclusively by {@link TopBarController}.
 */
public final class TopBarMediatorSupport {
	private TopBarMediatorSupport() {
	}

	public static void installBackTitle(ToolBarView toolBar, ActivityFragment fragment,
			ToolBarView.Mediator.BackTitle mediator) {
		toolBar.setVisibility(mediator.getVisibility(toolBar, fragment));
		TextView title = mediator.createTitleText(toolBar);
		mediator.addView(toolBar, title, mediator.getTitleId(), LEFT);
		if (mediator.backOnTitleClick()) title.setOnClickListener(mediator);

		var back = mediator.createBackButton(toolBar);
		mediator.addView(toolBar, back, mediator.getBackButtonId(),
				mediator.getBackButtonSide(toolBar));
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
