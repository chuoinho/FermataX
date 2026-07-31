package me.aap.fermata.ui.view;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

import java.util.function.Consumer;

import me.aap.utils.log.Log;

final class SurfaceCanvas {
	private SurfaceCanvas() {
	}

	static boolean draw(SurfaceHolder holder, Consumer<Canvas> drawer) {
		Canvas canvas = null;
		try {
			canvas = holder.lockCanvas();
			if (canvas == null) return false;
			drawer.accept(canvas);
			return true;
		} catch (RuntimeException error) {
			Log.e(error);
		} finally {
			if (canvas != null) {
				try {
					holder.unlockCanvasAndPost(canvas);
				} catch (RuntimeException error) {
					Log.e(error);
				}
			}
		}
		return false;
	}
}
