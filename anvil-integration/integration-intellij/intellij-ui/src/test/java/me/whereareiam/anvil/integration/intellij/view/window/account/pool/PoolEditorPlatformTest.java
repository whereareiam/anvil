package me.whereareiam.anvil.integration.intellij.view.window.account.pool;

import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBTextField;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.account.persistence.ConfiguredAccountLibrary;
import me.whereareiam.anvil.integration.intellij.model.account.AvailableAccount;
import me.whereareiam.anvil.integration.intellij.type.AccountSource;

public class PoolEditorPlatformTest extends UiPlatformTestCase {
	public void testMembershipSelectionAndRenameAreCommittedOnlyOnSave() throws Exception {
		WindowTestSupport.useDarcula(getTestRootDisposable());
		Path directory = Files.createTempDirectory("anvil-pool-ui-");
		var library = ConfiguredAccountLibrary.getInstance(getProject());
		var previous = library.getState();
		try {
			library.setDirectory(directory);
			library.savePool(null, "Players", List.of("missing"));
			var entry = new AvailableAccount(new AuthenticationAccount("alice", "mcprotocol", "Alice", null),
					directory.resolve("alice.json"), AccountSource.PROJECT);
			var dialog = new PoolEditorDialog(getProject(), library, "Players", library.catalog().getPools(), List.of(entry));
			Disposer.register(getTestRootDisposable(), dialog.getDisposable());
			var panel = dialog.createCenterPanel();
			var missing = WindowTestSupport.button(panel, "missing · Not currently available");
			assertTrue(missing.isSelected());
			missing.doClick();
			assertNotNull(dialog.doValidate());
			WindowTestSupport.button(panel, "alice · Project · Alice").doClick();
			WindowTestSupport.find(panel, JBTextField.class).setText("Test players");
			assertNull(dialog.doValidate());
			assertEquals(List.of("missing"), library.catalog().getPools().get("Players"));
			WindowTestSupport.capture(panel, "project-accounts-pool-editor", 500, 330);
			dialog.doOKAction();
			assertEquals(List.of("alice"), library.catalog().getPools().get("Test players"));
			assertFalse(library.catalog().getPools().containsKey("Players"));
			var cancelled = new PoolEditorDialog(getProject(), library, "Test players", library.catalog().getPools(), List.of(entry));
			Disposer.register(getTestRootDisposable(), cancelled.getDisposable());
			WindowTestSupport.find(cancelled.createCenterPanel(), JBTextField.class).setText("Unsaved");
			cancelled.doCancelAction();
			assertFalse(library.catalog().getPools().containsKey("Unsaved"));
		} finally {
			library.loadState(previous);
			com.intellij.openapi.util.io.FileUtil.delete(directory.toFile());
		}
	}
}
