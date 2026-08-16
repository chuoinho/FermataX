package me.aap.fermata.addon.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class WebUiShellIntegrationContractTest {
	@Test
	public void webFullscreenBackAndToolbarUseCommonBackAuthority() throws Exception {
		String fragment = source("modules/web/src/main/java/me/aap/fermata/addon/web/WebBrowserFragment.java");
		String toolbar = source("modules/web/src/main/java/me/aap/fermata/addon/web/WebToolBarMediator.java");

		assertTrue(fragment.contains("WebBackNavigationPolicy.resolve(fullScreen, v.canGoBack())"));
		assertTrue(fragment.contains("case EXIT_FULLSCREEN -> v.exitFullScreenForBack()"));
		assertTrue(fragment.contains("case WEB_HISTORY ->"));
		assertTrue(toolbar.contains("MainActivityDelegate.get(v.getContext()).onBackPressed()"));
		assertTrue(toolbar.contains("TopBarController.refresh(MainActivityDelegate.get(tb.getContext()), f)"));
		assertTrue(toolbar.contains("TopBarController.refresh(MainActivityDelegate.get(tb.getContext()))"));
		assertFalse(toolbar.contains("tool_bar_back_button).setVisibility"));
	}

	@Test
	public void youtubeTitleAndToolbarStayOnCommonTopBarAuthority() throws Exception {
		String youtube = source("modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeFragment.java");

		assertTrue(youtube.contains("implements FermataServiceUiBinder.Listener, TopBarPlaybackContext"));
		assertTrue(youtube.contains("YoutubeToolbarPolicy.usePlaybackTitle("));
		assertTrue(youtube.contains("TopBarController.refresh(a, f)"));
		assertFalse(youtube.contains("tb.setTitle("));
		assertFalse(youtube.contains("getToolBar().setTitle("));
	}

	private static String source(String relativePath) throws Exception {
		return Files.readString(projectRoot().resolve(relativePath), UTF_8);
	}

	private static Path projectRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			if (Files.isRegularFile(current.resolve("settings.gradle")) &&
					Files.isDirectory(current.resolve("modules/web"))) return current;
			current = current.getParent();
		}
		throw new AssertionError("Unable to locate FermataX project root");
	}
}
