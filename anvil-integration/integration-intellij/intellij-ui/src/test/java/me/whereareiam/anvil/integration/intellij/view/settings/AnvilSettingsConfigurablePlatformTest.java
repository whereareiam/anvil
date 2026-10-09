package me.whereareiam.anvil.integration.intellij.view.settings;

import com.intellij.ide.ui.laf.UiThemeProviderListManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.fields.IntegerField;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JComponent;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.settings.PersistentPreferences;
import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import me.whereareiam.anvil.integration.intellij.type.settings.CommandHistoryPersistence;
import me.whereareiam.anvil.integration.intellij.type.settings.SessionSection;

public class AnvilSettingsConfigurablePlatformTest extends UiPlatformTestCase {
	@Override
	protected boolean isIconRequired() {
		return true;
	}

	private PersistentPreferences settings;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		settings = new PersistentPreferences();
		ServiceContainerUtil.replaceService(
				ApplicationManager.getApplication(),
				Preferences.class,
				settings,
				getTestRootDisposable());
	}







	public void testNativeSettingsPageAppliesResetsAndReleasesItsUi() throws Exception {
		Configurable configurable = nativeConfigurable();
		try {
			assertEquals("Anvil", configurable.getDisplayName());
			assertEquals("me.whereareiam.anvil.settings", ((AnvilSettingsConfigurable) configurable).getId());
			var editor = (AnvilSettingsConfigurable) configurable;
			assertNotNull(editor);
			JComponent first = configurable.createComponent();
			assertNotNull(first);
			configurable.reset();
			var checkbox = (JBCheckBox) editor.getPreferredFocusedComponent();
			assertNotNull(checkbox);
			assertEquals("Expand scenarios automatically", checkbox.getText());
			assertFalse(checkbox.isSelected());
			assertFalse(configurable.isModified());
			checkbox.setSelected(true);
			assertTrue(configurable.isModified());
			assertFalse(settings.isExpandScenarioGroups());
			configurable.reset();
			assertFalse(checkbox.isSelected());
			checkbox.setSelected(true);
			configurable.apply();
			assertTrue(settings.isExpandScenarioGroups());
			assertFalse(configurable.isModified());
			configurable.disposeUIResources();
			assertFalse(editor.isModified());
			assertNull(editor.getPreferredFocusedComponent());
			assertNotSame(first, configurable.createComponent());
			configurable.reset();
			var reopened = (AnvilSettingsConfigurable) configurable;
			assertNotNull(reopened);
			assertTrue(((JBCheckBox) reopened.getPreferredFocusedComponent()).isSelected());
		} finally {
			configurable.disposeUIResources();
		}
	}



	public void testAllNativeControlsApplyTogetherAndInvalidRetentionCannotPartiallyApply()
			throws Exception {
		Configurable configurable = nativeConfigurable();
		List<PersistentPreferences.PreferenceState> changes = new ArrayList<>();
		settings.subscribe(() -> changes.add(settings.getState()), getTestRootDisposable());
		try {
			JComponent form = configurable.createComponent();
			configurable.reset();
			((JComboBox<?>) named(form, "defaultSessionSection")).setSelectedItem(SessionSection.PLAYERS);
			((JBCheckBox) named(form, "showConsoleOnFailure")).setSelected(false);
			((JComboBox<?>) named(form, "commandHistoryPersistence"))
					.setSelectedItem(CommandHistoryPersistence.PROJECT);
			((JBCheckBox) named(form, "refreshCatalogAfterSync")).setSelected(false);
			IntegerField retained = (IntegerField) named(form, "completedSuccessfulTabs");
			retained.setText("-1");
			try {
				configurable.apply();
				fail("An invalid retention count must prevent applying every preference");
			} catch (ConfigurationException expected) {
				assertTrue(changes.isEmpty());
				assertEquals(new PersistentPreferences.PreferenceState(), settings.getState());
			}
			retained.setValue(0);
			configurable.apply();
			assertEquals(1, changes.size());
			assertEquals(SessionSection.PLAYERS, settings.getDefaultSessionSection());
			assertFalse(settings.isShowConsoleOnFailure());
			assertEquals(0, settings.getCompletedSuccessfulTabs());
			assertEquals(CommandHistoryPersistence.PROJECT, settings.getCommandHistoryPersistence());
			assertFalse(settings.isRefreshCatalogAfterSync());
			assertFalse(configurable.isModified());
		} finally {
			configurable.disposeUIResources();
		}
	}

	public void testInvalidDirectoryKeepsEditsPendingWithoutApplyingOtherPreferences() throws Exception {
		var configurable = new AnvilSettingsConfigurable();
		try {
			JComponent form = configurable.createComponent();
			configurable.reset();
			((JBCheckBox) configurable.getPreferredFocusedComponent()).setSelected(true);
			var directory = (TextFieldWithBrowseButton) named(form, "accountsDirectory");
			directory.setText("invalid\0directory");
			assertThrows(ConfigurationException.class, configurable::apply);
			assertFalse(settings.isExpandScenarioGroups());
			assertTrue(configurable.isModified());

			directory.setText(settings.accountsDirectory().toString());
			configurable.apply();
			assertTrue(settings.isExpandScenarioGroups());
			assertEquals("", settings.snapshot().getAccountsDirectory());
			assertFalse(configurable.isModified());
		} finally {
			configurable.disposeUIResources();
		}
	}

	public void testNativeSettingsFormRendersInSupportedNativeThemes() throws Exception {
		for (String theme :
				List.of("ExperimentalDark", "ExperimentalLight", "Islands Dark", "Islands Light")) {
			if (UiThemeProviderListManager.Companion.getInstance().findThemeById(theme) == null) continue;
			WindowTestSupport.useTheme(getTestRootDisposable(), theme, theme.endsWith("Dark"));
			var configurable = new AnvilSettingsConfigurable();
			try {
				JComponent form = configurable.createComponent();
				configurable.reset();
				WindowTestSupport.capture(
						form, "anvil-settings-" + theme.replace(' ', '-').toLowerCase() + ".png",
						720, Math.max(470, form.getPreferredSize().height));
				for (String name :
						List.of(
								"defaultSessionSection",
								"showConsoleOnFailure",
								"completedSuccessfulTabs",
								"commandHistoryPersistence",
								"refreshCatalogAfterSync",
								"accountsDirectory")) {
					Component control = named(form, name);
					assertTrue(name, control.getWidth() > 0 && control.getHeight() > 0);
				}
			} finally {
				configurable.disposeUIResources();
			}
		}
	}

	public void testCancellingSettingsDoesNotChangeStoredPreferenceOrNotifyViews() {
		List<Boolean> changes = new ArrayList<>();
		settings.subscribe(
				() -> changes.add(settings.isExpandScenarioGroups()), getTestRootDisposable());
		Configurable configurable = nativeConfigurable();
		try {
			configurable.createComponent();
			configurable.reset();
			var editor = (AnvilSettingsConfigurable) configurable;
			assertNotNull(editor);
			((JBCheckBox) editor.getPreferredFocusedComponent()).setSelected(true);
			configurable.cancel();
		} finally {
			configurable.disposeUIResources();
		}
		assertFalse(settings.isExpandScenarioGroups());
		assertTrue(changes.isEmpty());
	}

	private static Component named(Container parent, String name) {
		for (Component child : parent.getComponents()) {
			if (name.equals(child.getName())) return child;
			if (child instanceof Container nested) {
				Component found = named(nested, name);
				if (found != null) return found;
			}
		}
		return null;
	}

	private Configurable nativeConfigurable() {
		return new AnvilSettingsConfigurable();
	}
}
