package me.whereareiam.anvil.integration.intellij.scenario.settings;

import com.intellij.ide.ui.laf.UiThemeProviderListManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.fields.IntegerField;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JComponent;

import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.SwingTestSupport;

public class ScenarioSettingsPagePlatformTest extends ScenarioPlatformTestCase {
	private ScenarioSettings settings;

	@Override
	protected boolean isIconRequired() {
		return true;
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		settings = new ScenarioSettings();
		ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), ScenarioSettings.class, settings, getTestRootDisposable());
	}

	public void testPageIsNestedUnderAnvilInToolsSettings() {
		var page = Configurable.APPLICATION_CONFIGURABLE.getExtensionList().stream()
				.filter(candidate -> ScenarioSettingsPage.ID.equals(candidate.id))
				.findFirst()
				.orElseThrow();
		assertEquals("me.whereareiam.anvil", page.parentId);
		assertEquals("Scenarios", page.displayName);
	}

	public void testPageAppliesResetsAndReleasesItsUi() throws Exception {
		var page = new ScenarioSettingsPage();
		try {
			assertEquals("Scenarios", page.getDisplayName());
			JComponent first = page.createComponent();
			page.reset();
			var checkbox = (JBCheckBox) page.getPreferredFocusedComponent();
			assertNotNull(checkbox);
			assertEquals("Expand scenarios automatically", checkbox.getText());
			assertFalse(checkbox.isSelected());
			assertFalse(page.isModified());

			checkbox.setSelected(true);
			assertTrue(page.isModified());
			assertFalse(settings.values().isExpandScenarios());
			page.reset();
			assertFalse(checkbox.isSelected());

			checkbox.setSelected(true);
			page.apply();
			assertTrue(settings.values().isExpandScenarios());
			assertFalse(page.isModified());

			page.disposeUIResources();
			assertFalse(page.isModified());
			assertNull(page.getPreferredFocusedComponent());
			assertNotSame(first, page.createComponent());
			page.reset();
			assertTrue(((JBCheckBox) page.getPreferredFocusedComponent()).isSelected());
		} finally {
			page.disposeUIResources();
		}
	}

	public void testAllControlsApplyTogetherAndInvalidRetentionAppliesNothing() throws Exception {
		var page = new ScenarioSettingsPage();
		List<ScenarioSettings.Values> changes = new ArrayList<>();
		settings.subscribe(() -> changes.add(settings.values()), getTestRootDisposable());
		try {
			JComponent form = page.createComponent();
			page.reset();
			((JComboBox<?>) named(form, "openingSection")).setSelectedItem(ScenarioSettings.Section.PLAYERS);
			((JBCheckBox) named(form, "revealConsoleOnFailure")).setSelected(false);
			((JComboBox<?>) named(form, "commandHistory")).setSelectedItem(ScenarioSettings.History.PROJECT);
			((JBCheckBox) named(form, "refreshAfterSync")).setSelected(false);
			IntegerField retained = (IntegerField) named(form, "completedTabs");
			retained.setText("-1");
			assertThrows(ConfigurationException.class, page::apply);
			assertTrue(changes.isEmpty());
			assertEquals(ScenarioSettings.Values.builder().build(), settings.values());

			retained.setValue(0);
			page.apply();
			assertEquals(1, changes.size());
			var applied = settings.values();
			assertEquals(ScenarioSettings.Section.PLAYERS, applied.getOpeningSection());
			assertFalse(applied.isRevealConsoleOnFailure());
			assertEquals(0, applied.getCompletedTabs());
			assertEquals(ScenarioSettings.History.PROJECT, applied.getCommandHistory());
			assertFalse(applied.isRefreshAfterSync());
			assertFalse(page.isModified());
		} finally {
			page.disposeUIResources();
		}
	}

	public void testFormRendersInSupportedThemes() throws Exception {
		for (String theme : List.of("ExperimentalDark", "ExperimentalLight", "Islands Dark", "Islands Light")) {
			if (UiThemeProviderListManager.Companion.getInstance().findThemeById(theme) == null) continue;
			SwingTestSupport.useTheme(getTestRootDisposable(), theme, theme.endsWith("Dark"));
			var page = new ScenarioSettingsPage();
			try {
				JComponent form = page.createComponent();
				page.reset();
				SwingTestSupport.capture(form, "anvil-scenario-settings-" + theme.replace(' ', '-').toLowerCase() + ".png",
						720, Math.max(420, form.getPreferredSize().height));
				for (String name : List.of("openingSection", "revealConsoleOnFailure", "completedTabs", "commandHistory", "refreshAfterSync")) {
					Component control = named(form, name);
					assertTrue(name, control.getWidth() > 0 && control.getHeight() > 0);
				}
			} finally {
				page.disposeUIResources();
			}
		}
	}

	public void testCancellingKeepsStoredSettingsAndNotifiesNobody() {
		List<Boolean> changes = new ArrayList<>();
		settings.subscribe(() -> changes.add(settings.values().isExpandScenarios()), getTestRootDisposable());
		var page = new ScenarioSettingsPage();
		try {
			page.createComponent();
			page.reset();
			((JBCheckBox) page.getPreferredFocusedComponent()).setSelected(true);
			page.cancel();
		} finally {
			page.disposeUIResources();
		}

		assertFalse(settings.values().isExpandScenarios());
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
}
