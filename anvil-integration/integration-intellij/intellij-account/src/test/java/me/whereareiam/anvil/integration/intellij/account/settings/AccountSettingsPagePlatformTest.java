package me.whereareiam.anvil.integration.intellij.account.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.components.JBCheckBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JComponent;

import me.whereareiam.anvil.integration.intellij.AccountPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.SwingTestSupport;
import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;

public class AccountSettingsPagePlatformTest extends AccountPlatformTestCase {
	public void testPageIsNestedUnderAnvilAsAProjectPage() {
		var page = Configurable.PROJECT_CONFIGURABLE.getExtensions(getProject()).stream()
				.filter(candidate -> AccountSettingsPage.ID.equals(candidate.id))
				.findFirst()
				.orElseThrow();
		assertEquals("me.whereareiam.anvil", page.parentId);
		assertEquals("Accounts", page.displayName);
	}

	public void testDirectoriesApplyTogetherAndRelativePathsResolveAgainstTheProject() throws Exception {
		var page = new AccountSettingsPage(getProject());
		Path global = temporary("global-accounts");
		try {
			JComponent form = page.createComponent();
			page.reset();
			var fields = fields(form);
			var include = SwingTestSupport.find(form, JBCheckBox.class);
			assertFalse(page.isModified());

			fields.get(0).setText(global.toString());
			fields.get(1).setText("accounts");
			include.setSelected(false);
			assertTrue(page.isModified());
			page.apply();

			assertEquals(global, AccountSettings.getInstance().directory());
			assertEquals(Path.of(getProject().getBasePath()).resolve("accounts").normalize(),
					AccountLibrary.getInstance(getProject()).directory());
			assertFalse(AccountLibrary.getInstance(getProject()).includesGlobal());
			assertEquals("accounts", fields.get(1).getText());
			assertFalse(page.isModified());
		} finally {
			page.disposeUIResources();
		}
	}

	public void testAFileOrAnEmptyPathIsRefusedAndNothingIsApplied() throws Exception {
		var page = new AccountSettingsPage(getProject());
		Path file = Files.createTempFile("anvil-accounts-", ".json");
		Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(file.toFile()));
		try {
			JComponent form = page.createComponent();
			page.reset();
			var fields = fields(form);

			fields.get(0).setText(file.toString());
			assertThrows(ConfigurationException.class, page::apply);
			fields.get(0).setText(" ");
			assertThrows(ConfigurationException.class, page::apply);

			assertEquals(AccountSettings.getInstance().defaultDirectory(), AccountSettings.getInstance().directory());
		} finally {
			page.disposeUIResources();
		}
	}

	public void testChoosingTheDefaultDirectoryClearsTheOverride() throws Exception {
		var settings = AccountSettings.getInstance();
		settings.setDirectory(temporary("custom"));
		settings.setDirectory(settings.defaultDirectory());

		assertEquals("", settings.getState().getDirectory());
	}

	private static List<TextFieldWithBrowseButton> fields(JComponent form) {
		var found = new java.util.ArrayList<TextFieldWithBrowseButton>();
		collect(form, found);
		assertEquals(2, found.size());
		return found;
	}

	private static void collect(java.awt.Container parent, List<TextFieldWithBrowseButton> found) {
		for (var child : parent.getComponents()) {
			if (child instanceof TextFieldWithBrowseButton field) {
				found.add(field);
				continue;
			}
			if (child instanceof java.awt.Container nested) collect(nested, found);
		}
	}

	private Path temporary(String prefix) throws Exception {
		Path path = Files.createTempDirectory(prefix);
		Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(path.toFile()));
		return path;
	}
}
