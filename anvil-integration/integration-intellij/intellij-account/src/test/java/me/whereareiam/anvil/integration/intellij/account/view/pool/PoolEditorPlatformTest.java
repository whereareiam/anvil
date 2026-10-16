package me.whereareiam.anvil.integration.intellij.account.view.pool;

import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBTextField;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import me.whereareiam.anvil.api.model.Edition;
import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.integration.intellij.AccountPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.SwingTestSupport;
import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.account.AccountSource;
import me.whereareiam.anvil.integration.intellij.account.AvailableAccount;
import me.whereareiam.anvil.integration.intellij.account.view.EditionChip;

public class PoolEditorPlatformTest extends AccountPlatformTestCase {
	public void testMembershipSelectionAndRenameAreCommittedOnlyOnSave() throws Exception {
		SwingTestSupport.useDarcula(getTestRootDisposable());
		Path directory = Files.createTempDirectory("anvil-pool-ui-");
		var library = AccountLibrary.getInstance(getProject());
		var previous = library.getState();
		try {
			library.setDirectory(directory);
			library.savePool(null, "Players", List.of("missing"));
			var entry = new AvailableAccount(new AuthenticationAccount("alice", List.of(AccountAccess.available(Edition.of("java"), "Alice", UUID.randomUUID()))), List.of(),
					directory.resolve("alice.json"), AccountSource.PROJECT);
			var dialog = new PoolEditorDialog(getProject(), library, "Players", library.catalog().getPools(), List.of(entry));
			Disposer.register(getTestRootDisposable(), dialog.getDisposable());
			var panel = dialog.createCenterPanel();
			var missing = SwingTestSupport.button(panel, "missing · Not currently available");
			assertTrue(missing.isSelected());
			missing.doClick();
			assertNotNull(dialog.doValidate());
			var alice = SwingTestSupport.button(panel, "alice · Project · Alice");
			assertEquals("alice · Project · Alice, available for Java", alice.getAccessibleContext().getAccessibleName());
			assertEquals("Java", SwingTestSupport.find(alice.getParent(), EditionChip.class).getText());
			alice.doClick();
			SwingTestSupport.find(panel, JBTextField.class).setText("Test players");
			assertNull(dialog.doValidate());
			assertEquals(List.of("missing"), library.catalog().getPools().get("Players"));
			SwingTestSupport.capture(panel, "project-accounts-pool-editor", 500, 330);
			dialog.doOKAction();
			assertEquals(List.of("alice"), library.catalog().getPools().get("Test players"));
			assertFalse(library.catalog().getPools().containsKey("Players"));
			var cancelled = new PoolEditorDialog(getProject(), library, "Test players", library.catalog().getPools(), List.of(entry));
			Disposer.register(getTestRootDisposable(), cancelled.getDisposable());
			SwingTestSupport.find(cancelled.createCenterPanel(), JBTextField.class).setText("Unsaved");
			cancelled.doCancelAction();
			assertFalse(library.catalog().getPools().containsKey("Unsaved"));
		} finally {
			library.loadState(previous);
			com.intellij.openapi.util.io.FileUtil.delete(directory.toFile());
		}
	}
}
