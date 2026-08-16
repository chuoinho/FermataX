package me.aap.fermata.ui.view;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class TopBarAuthorityContractTest {
	@Test
	public void commonControllerOwnsBackAndPlaybackAwareTitle() throws Exception {
		String controller = coreSource("ui/view/TopBarController.java");
		assertTrue(controller.contains("TopBarPolicy.resolve("));
		assertTrue(controller.contains("snapshot.getDisplayTitle()"));
		assertTrue(controller.contains("ItemRoutePolicy.getPlaybackOwnerFragmentId(item)"));
		assertTrue(controller.contains("fragment instanceof TopBarPlaybackContext context"));
		assertTrue(controller.contains("back.getVisibility() != state.backVisibility()"));
		assertTrue(controller.contains("!TextUtils.equals(title.getText(), state.title())"));
		assertTrue(controller.contains("back.setVisibility(state.backVisibility())"));
		assertTrue(controller.contains("title.setText(state.title())"));
	}

	@Test
	public void structuralInstallerNeverResolvesTitleOrRouteBackVisibility() throws Exception {
		String support = coreSource("ui/view/TopBarMediatorSupport.java");
		assertTrue(support.contains("installBackTitle("));
		assertTrue(support.contains("installBackTitleFilter("));
		assertFalse(support.contains("title.setText("));
		assertFalse(support.contains("back.setVisibility("));
		assertFalse(support.contains("TopBarPolicy.resolve("));
	}

	@Test
	public void mediaToolbarAppliesCommonControllerOnEnableAndLifecycleChanges() throws Exception {
		String mediator = coreSource("ui/fragment/ToolBarMediator.java");
		assertTrue(mediator.contains("TopBarMediatorSupport.installBackTitleFilter(tb, f, this)"));
		assertTrue(mediator.contains("TopBarController.refresh(a, f);"));
		assertTrue(mediator.contains("TopBarController.refresh(activity, f);"));
		assertFalse(mediator.contains("BackTitleFilter.super.enable(tb, f)"));
		assertFalse(mediator.contains(
				"tb.findViewById(me.aap.utils.R.id.tool_bar_back_button);"));
		assertFalse(mediator.contains("BackTitleFilter.super.onActivityEvent"));
	}

	@Test
	public void playerPresentationInvalidatesShellWithoutWritingTopBarViews() throws Exception {
		String presentation = coreSource("ui/view/ControlPanelPresentationView.java");
		assertTrue(presentation.contains("UiShellController.onPlaybackPresentationChanged(activity);"));
		assertFalse(presentation.contains("TopBarController"));
		assertFalse(presentation.contains("tool_bar_title"));
		assertFalse(presentation.contains("title.setText"));
		assertFalse(presentation.contains("usesAutomotivePresentation()) return"));

		String shell = coreSource("ui/view/UiShellController.java");
		assertTrue(shell.contains("TopBarController.refresh(activity);"));
	}

	@Test
	public void compatibilityChromeFacadeDoesNotWriteTopBarViews() throws Exception {
		String chrome = coreSource("ui/policy/ChromePolicy.java");
		assertTrue(chrome.contains("TopBarPolicy.isTopBackVisible"));
		assertTrue(chrome.contains("TopBarController.refresh(a);"));
		assertFalse(chrome.contains("findViewById"));
		assertFalse(chrome.contains("setVisibility("));
	}

	@Test
	public void webBackUsesCommonActivityBackAndCommonVisibilityAuthority() throws Exception {
		String web = repositorySource(
				"modules/web/src/main/java/me/aap/fermata/addon/web/WebToolBarMediator.java");
		assertTrue(web.contains("MainActivityDelegate.get(v.getContext()).onBackPressed()"));
		assertTrue(web.contains("TopBarController.refresh(MainActivityDelegate.get(tb.getContext()))"));
		assertFalse(web.contains(
				"findViewById(me.aap.utils.R.id.tool_bar_back_button).setVisibility"));
	}

	private static String coreSource(String relativePath) throws Exception {
		return repositorySource("fermata/src/main/java/me/aap/fermata/" + relativePath);
	}

	private static String repositorySource(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(relativePath)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
