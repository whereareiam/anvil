package me.whereareiam.anvil.integration.intellij.account.view;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import me.whereareiam.anvil.integration.intellij.AccountPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.SwingTestSupport;
import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.account.settings.AccountSettings;
import me.whereareiam.anvil.integration.intellij.account.view.pool.PoolsPanel;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;

public class AccountsDialogPlatformTest extends AccountPlatformTestCase {
	public void testProjectAndGlobalAccountsAreListedWithTheirSource() throws Exception {
		var library = AccountLibrary.getInstance(getProject());
		Path project = temporary("project-accounts");
		Path global = temporary("global-accounts");
		library.setDirectory(project);
		library.setIncludesGlobal(true);
		AccountSettings.getInstance().setDirectory(global);
		Files.writeString(project.resolve("alice.json"), account("alice", "Alice"));
		Files.writeString(global.resolve("bob.json"), account("bob", "Bob"));
		library.setIncludesGlobal(false);
		var single = dialog(project);
		SwingTestSupport.await(() -> SwingTestSupport.text(single).contains("1 account available to this project."));
		library.setIncludesGlobal(true);

		var view = dialog(project);
		var table = SwingTestSupport.find(SwingTestSupport.find(view, AccountListPanel.class), JBTable.class);
		SwingTestSupport.await(() -> table.getRowCount() == 2);

		assertEquals("Project", table.getValueAt(0, 2));
		assertEquals("Global", table.getValueAt(1, 2));
		assertEquals("Bob", table.getValueAt(1, 0));
		assertEquals(3, table.getColumnCount());
		var cell = (Container) table.prepareRenderer(table.getCellRenderer(1, 0), 1, 0);
		assertEquals("Bob, available for Java", cell.getAccessibleContext().getAccessibleName());
		assertEquals(List.of("Java"), chips(cell));
		assertTrue(SwingTestSupport.text(view).contains("2 accounts available to this project."));
	}

	public void testEmptyProjectOffersSignInAndImportInTheList() throws Exception {
		SwingTestSupport.useDarcula(getTestRootDisposable());
		var library = AccountLibrary.getInstance(getProject());
		Path directory = temporary("project-account-preview");
		library.setDirectory(directory);
		library.setIncludesGlobal(false);

		var view = dialog(directory);
		var accounts = SwingTestSupport.find(view, AccountListPanel.class);
		var table = SwingTestSupport.find(accounts, JBTable.class);
		SwingTestSupport.await(() -> SwingTestSupport.text(view).contains("0 accounts available"));

		assertEquals("No accounts available to this project", table.getEmptyText().getText());
		String offers = table.getEmptyText().getSecondaryComponent().getCharSequence(false).toString();
		assertTrue(offers, offers.contains("Sign in") && offers.contains("import an account file"));
		assertNotNull(SwingTestSupport.button(view, "Close"));
		assertNull(SwingTestSupport.button(view, "OK"));
		SwingTestSupport.capture(view, "project-accounts-empty", 780, 550);
	}

	public void testPoolsAndAccountsRenderAfterRefresh() throws Exception {
		SwingTestSupport.useDarcula(getTestRootDisposable());
		var library = AccountLibrary.getInstance(getProject());
		Path directory = temporary("project-account-pools");
		library.setDirectory(directory);
		library.setIncludesGlobal(false);
		Files.writeString(directory.resolve("alice.json"), account("alice", "Alice"));
		Files.writeString(directory.resolve("bob.json"), account("bob", "Bob"));
		library.savePool(null, "Test players", List.of("alice", "bob"));

		var view = dialog(directory);
		var table = SwingTestSupport.find(SwingTestSupport.find(view, AccountListPanel.class), JBTable.class);
		SwingTestSupport.await(() -> table.getRowCount() == 2);
		SwingTestSupport.capture(view, "project-accounts-populated", 780, 550);
		SwingTestSupport.find(view, JBTabbedPane.class).setSelectedIndex(1);
		var pools = SwingTestSupport.find(SwingTestSupport.find(view, PoolsPanel.class), JBTable.class);

		assertEquals(1, pools.getRowCount());
		assertEquals("Test players", pools.getValueAt(0, 0));
		assertEquals("alice, bob", pools.getValueAt(0, 1));
		SwingTestSupport.capture(view, "project-accounts-pools", 780, 550);
	}

	public void testProblemsThatWouldStopARunAreShown() throws Exception {
		var library = AccountLibrary.getInstance(getProject());
		Path project = temporary("project-duplicates");
		Path global = temporary("global-duplicates");
		library.setDirectory(project);
		library.setIncludesGlobal(true);
		AccountSettings.getInstance().setDirectory(global);
		Files.writeString(project.resolve("alice.json"), account("alice", "Alice"));
		Files.writeString(global.resolve("alice.json"), account("alice", "Alice"));

		var view = dialog(project);

		SwingTestSupport.await(() -> SwingTestSupport.text(view).contains("Duplicate account ID 'alice'"));
	}

	private List<String> chips(Container cell) {
		return Arrays.stream(cell.getComponents())
				.filter(EditionChip.class::isInstance)
				.map(chip -> ((EditionChip) chip).getText())
				.toList();
	}

	private JComponent dialog(Path directory) {
		ScenarioSource source = ScenarioSource.builder().id("fixture").displayName("Example").integrationId("gradle")
				.directory(directory).build();
		var dialog = new AccountsDialog(getProject(), source);
		Disposer.register(getTestRootDisposable(), dialog.getDisposable());
		var panel = new JPanel(new BorderLayout(0, 12));
		panel.setBorder(JBUI.Borders.empty(12));
		panel.add(dialog.createCenterPanel(), BorderLayout.CENTER);
		var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		assertEquals(1, dialog.createActions().length);
		footer.add(new JButton(dialog.createActions()[0]));
		panel.add(footer, BorderLayout.SOUTH);
		return panel;
	}

	private Path temporary(String prefix) throws Exception {
		Path path = Files.createTempDirectory(prefix);
		Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(path.toFile()));
		return path;
	}

	private static String account(String id, String user) {
		return "{\"schemaVersion\":3,\"accountId\":\"" + id + "\",\"microsoft\":{},\"editions\":{"
				+ "\"java\":{\"availability\":\"AVAILABLE\",\"username\":\"" + user + "\",\"uuid\":\"" + UUID.randomUUID() + "\"},"
				+ "\"bedrock\":{\"availability\":\"UNAVAILABLE\",\"reason\":\"Account '" + id + "' owns no game\"}},\"sessions\":{}}";
	}
}
