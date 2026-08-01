package me.aap.fermata.addon.web.yt;

import me.aap.fermata.R;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;

final class YoutubeQualityMenu {
	private static final int QUALITY_MASK = 1 << 31;

	static FutureSupplier<Void> populate(YoutubeWebView web, OverlayMenu.Builder menu,
			OverlayMenu.SelectionHandler handler) {
		menu.setSelectionHandler(handler);
		return web.getVideoQualities().timeout(1700).main()
				.onFailure(err -> Log.e(err, "Failed to load video qualities"))
				.map(qualities -> {
					if ((qualities == null) || qualities.isEmpty()) {
						menu.addItem(R.id.auto, null, R.string.auto).setChecked(true, true);
						return null;
					}

					String[] options = qualities.split(";");
					for (int i = 0; i < options.length; i++) {
						String option = options[i];
						boolean selected = option.startsWith("*");
						if (selected) option = option.substring(1);
						menu.addItem(UiUtils.getArrayItemId(i), null, option)
								.setChecked(selected, true).setData(i | QUALITY_MASK);
					}
					return null;
				});
	}

	static boolean select(YoutubeWebView web, OverlayMenuItem item) {
		if (!(item.getData() instanceof Integer value) || ((value & QUALITY_MASK) == 0))
			return false;
		web.setVideoQuality(value & ~QUALITY_MASK);
		return true;
	}

	private YoutubeQualityMenu() {
	}
}
