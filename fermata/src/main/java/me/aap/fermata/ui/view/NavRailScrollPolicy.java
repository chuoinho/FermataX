package me.aap.fermata.ui.view;

final class NavRailScrollPolicy {
	private NavRailScrollPolicy() {
	}

	static int maxScroll(int contentHeight, int viewportHeight, int affordanceExtent) {
		int overflow = contentHeight - viewportHeight;
		return (overflow <= 0) ? 0 : overflow + Math.max(0, affordanceExtent);
	}

	static int visibleTop(int scrollY, int affordanceExtent) {
		return scrollY + ((scrollY > 0) ? Math.max(0, affordanceExtent) : 0);
	}

	static int visibleBottom(int scrollY, int viewportHeight, int maxScroll,
			int affordanceExtent) {
		return scrollY + viewportHeight -
				((scrollY < maxScroll) ? Math.max(0, affordanceExtent) : 0);
	}

	static boolean isAffordanceTouch(float y, int viewportHeight, int scrollY,
			int maxScroll, int affordanceExtent) {
		if ((maxScroll <= 0) || (viewportHeight <= 0)) return false;
		int extent = Math.min(Math.max(0, affordanceExtent), viewportHeight / 3);
		return ((scrollY > 0) && (y < extent)) ||
				((scrollY < maxScroll) && (y >= viewportHeight - extent));
	}

	static int nextScroll(int scrollY, float distanceY, int maxScroll) {
		return Math.max(0, Math.min(Math.max(0, maxScroll), scrollY + (int) distanceY));
	}
}
