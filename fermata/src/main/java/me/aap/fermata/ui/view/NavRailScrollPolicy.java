package me.aap.fermata.ui.view;

final class NavRailScrollPolicy {
	private NavRailScrollPolicy() {
	}

	static int nextScroll(int scrollY, float distanceY, int maxScroll) {
		return Math.max(0, Math.min(Math.max(0, maxScroll), scrollY + (int) distanceY));
	}

	static boolean canScrollUp(int scrollY) {
		return scrollY > 0;
	}

	static boolean canScrollDown(int scrollY, int maxScroll) {
		return scrollY < Math.max(0, maxScroll);
	}
}
