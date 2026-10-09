package me.whereareiam.anvil.integration.intellij.view.window.account;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JToggleButton;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.account.persistence.ConfiguredAccountLibrary;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.settings.PersistentPreferences;
import me.whereareiam.anvil.integration.intellij.view.window.account.component.AccountListPanel;
import me.whereareiam.anvil.integration.intellij.view.window.account.component.AccountStoragePanel;

public class ProjectAccountsDialogPlatformTest extends UiPlatformTestCase {
	public void testAccountSourcesRefreshImmediatelyAndClosePreservesChanges() throws Exception {
		var settings = PersistentPreferences.getInstance();
		var previous = settings.getState();
		var library = ConfiguredAccountLibrary.getInstance(getProject());
		var previousProject = library.getState();
		Path project = temporary("project-accounts");
		Path global = temporary("global-accounts");
		try {
			library.setDirectory(project);
			library.setIncludesGlobal(true);
			settings.setAccountsDirectory(global);
			Files.writeString(project.resolve("alice.json"), account("alice", "Alice"));
			Files.writeString(global.resolve("bob.json"), account("bob", "Bob"));
			var dialog = dialog(project);
			var panel = WindowTestSupport.find(dialog.panel(), AccountListPanel.class);
			var table = WindowTestSupport.find(panel, JBTable.class);
			WindowTestSupport.await(() -> table.getRowCount() == 2);
			assertFalse(WindowTestSupport.button(panel, "Export…").isEnabled());
			table.setRowSelectionInterval(1, 1);
			assertTrue(WindowTestSupport.button(panel, "Export…").isEnabled());
			assertFalse(WindowTestSupport.button(panel, "Remove").isEnabled());
			assertEquals("Global", table.getValueAt(1, 2));
			var toggle = WindowTestSupport.find(panel, JBCheckBox.class);
			toggle.doClick();
			WindowTestSupport.await(() -> table.getRowCount() == 1);
			assertFalse(library.includesGlobal());
			dialog.dialog().doCancelAction();
			assertFalse(library.includesGlobal());
			assertEquals(global, settings.accountsDirectory());
		} finally {
			library.loadState(previousProject);
			settings.loadState(previous);
		}
	}

	public void testStorageAppliesExplicitlyAndRendersAccountAndPoolStates() throws Exception {
		WindowTestSupport.useDarcula(getTestRootDisposable());
		var library = ConfiguredAccountLibrary.getInstance(getProject());
		var previous = library.getState();
		Path directory = temporary("project-account-preview");
		try {
			library.setDirectory(directory);
			library.setIncludesGlobal(false);
			var dialog = dialog(directory);
			var accounts = WindowTestSupport.find(dialog.panel(), AccountListPanel.class);
			WindowTestSupport.await(() -> WindowTestSupport.text(dialog.panel()).contains("0 accounts available"));
			assertTrue(WindowTestSupport.text(accounts).contains("No accounts available"));
			assertNull(WindowTestSupport.button(dialog.panel(), "OK"));
			assertNotNull(WindowTestSupport.button(dialog.panel(), "Close"));
			WindowTestSupport.capture(dialog.panel(), "project-accounts-empty", 780, 550);
			Files.writeString(directory.resolve("alice.json"), account("alice", "Alice"));
			Files.writeString(directory.resolve("bob.json"), account("bob", "Bob"));
			library.savePool(null, "Test players", List.of("alice", "bob"));
			WindowTestSupport.button(accounts, "Refresh").doClick();
			var table = WindowTestSupport.find(accounts, JBTable.class);
			WindowTestSupport.await(() -> table.getRowCount() == 2);
			table.setRowSelectionInterval(0, 0);
			WindowTestSupport.capture(dialog.panel(), "project-accounts-populated", 780, 550);
			var tabs = WindowTestSupport.find(dialog.panel(), JBTabbedPane.class);
			tabs.setSelectedIndex(1);
			WindowTestSupport.capture(dialog.panel(), "project-accounts-pools", 780, 550);
			var storage = WindowTestSupport.find(dialog.panel(), AccountStoragePanel.class);
			WindowTestSupport.find(storage, JToggleButton.class).doClick();
			var path = WindowTestSupport.find(storage, com.intellij.openapi.ui.TextFieldWithBrowseButton.class);
			Path other = directory.resolve("other");
			path.setText(other.toString());
			assertEquals(directory, library.directory());
			WindowTestSupport.button(storage, "Apply Location").doClick();
			assertEquals(other, library.directory());
			WindowTestSupport.await(() -> table.getRowCount() == 0);
			tabs.setSelectedIndex(0);
			WindowTestSupport.capture(dialog.panel(), "project-accounts-storage", 780, 680);
		} finally { library.loadState(previous); }
	}

	public void testCatalogFailureReleasesBusyControls() throws Exception {
		var library = ConfiguredAccountLibrary.getInstance(getProject());
		var failing = (AccountLibrary) Proxy.newProxyInstance(
				AccountLibrary.class.getClassLoader(),
				new Class<?>[] {AccountLibrary.class},
				(proxy, method, arguments) -> {
					if (method.getName().equals("catalog")) throw new IllegalStateException("Account directory unavailable");
					return method.invoke(library, arguments);
				});
		ServiceContainerUtil.replaceService(getProject(), AccountLibrary.class, failing, getTestRootDisposable());

		var dialog = dialog(temporary("failing-accounts"));
		var accounts = WindowTestSupport.find(dialog.panel(), AccountListPanel.class);
		WindowTestSupport.await(() -> WindowTestSupport.text(dialog.panel())
				.contains("Could not load project accounts: Account directory unavailable"));
		assertTrue(WindowTestSupport.button(accounts, "Import…").isEnabled());
	}

	private View dialog(Path directory) {
		ScenarioSource source = ScenarioSource.builder().id("fixture").displayName("Example").integrationId("gradle").directory(directory).build();
		var dialog = new ProjectAccountsDialog(getProject(), source);
		Disposer.register(getTestRootDisposable(), dialog.getDisposable());
		var panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 12));
		panel.setBorder(com.intellij.util.ui.JBUI.Borders.empty(12));
		panel.add(dialog.createCenterPanel(), java.awt.BorderLayout.CENTER);
		var footer = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
		assertEquals(1, dialog.createActions().length);
		footer.add(new JButton(dialog.createActions()[0]));
		panel.add(footer, java.awt.BorderLayout.SOUTH);
		return new View(dialog, panel);
	}

	private Path temporary(String prefix) throws Exception {
		Path path = Files.createTempDirectory(prefix);
		Disposer.register(getTestRootDisposable(), () -> com.intellij.openapi.util.io.FileUtil.delete(path.toFile()));
		return path;
	}

	private record View(ProjectAccountsDialog dialog, javax.swing.JComponent panel) {}

	private String account(String id, String user) {
		return "{\"schemaVersion\":1,\"accountId\":\"" + id + "\",\"provider\":\"mcprotocol\",\"username\":\""
				+ user + "\",\"credentials\":{}}";
	}
}
