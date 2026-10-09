package me.whereareiam.anvil.integration.intellij.view.window.main.navigation;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import org.jetbrains.annotations.NotNull;

public class WorkspaceNavigatorPlatformTest extends UiPlatformTestCase {
	@Override
	protected @NotNull TempDirTestFixture createTempDirTestFixture() {
		return new TempDirTestFixtureImpl();
	}

	public void testCleanedWorkspaceReportsWhyTheLinkCannotOpen() throws Exception {
		Path parent = myFixture.getTempDirFixture().findOrCreateDir("workspaces").toNioPath();
		Path missing = parent.resolve("missing-anvil-workspace");
		assertTrue(Files.isDirectory(parent));
		assertFalse(Files.exists(missing));
		List<String> status = new ArrayList<>();
		new WorkspaceNavigator(getProject()).open(
				missing.toString(), getTestRootDisposable(), status::add);
		WindowTestSupport.await(() -> status.size() == 2);
		assertEquals("Locating workspace…", status.getFirst());
		assertTrue(status.getLast().contains("no longer available"));
		assertTrue(status.getLast().contains("cleaned it up"));
	}

	public void testInvalidLocalWorkspacePathReportsAReadableFailure() throws Exception {
		List<String> status = new ArrayList<>();
		new WorkspaceNavigator(getProject()).open(
				"invalid\u0000path", getTestRootDisposable(), status::add);
		WindowTestSupport.await(() -> status.size() == 2);
		assertEquals("The workspace path is not valid on this computer.", status.getLast());
	}
	public void testDisposedOwnerDoesNotStartNavigationOrReceiveStatus() {
		var owner = Disposer.newDisposable();
		Disposer.dispose(owner);
		List<String> statuses = new ArrayList<>();

		new WorkspaceNavigator(getProject()).open("/unused", owner, statuses::add);

		assertTrue(statuses.isEmpty());
	}

}
