package me.whereareiam.anvil.integration.intellij.scenario.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;

import javax.swing.JComponent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Settings | Tools | Anvil | Scenarios, bound to the IDE's Apply and Reset lifecycle.
 */
public final class ScenarioSettingsPage implements SearchableConfigurable {
	/**
	 * Settings page identifier, for opening the page from other screens.
	 */
	public static final String ID = "me.whereareiam.anvil.scenarios";

	private @Nullable ScenarioSettingsForm form;

	@Override
	public @NotNull String getId() {
		return ID;
	}

	@Override
	public @NotNull String getDisplayName() {
		return "Scenarios";
	}

	@Override
	public @NotNull JComponent createComponent() {
		if (form == null) form = new ScenarioSettingsForm();
		return form.getComponent();
	}

	@Override
	public boolean isModified() {
		return form != null && form.isModified();
	}

	@Override
	public void apply() throws ConfigurationException {
		if (form == null) return;

		ScenarioSettings.getInstance().update(form.apply());
		reset();
	}

	@Override
	public void reset() {
		if (form != null) form.reset(ScenarioSettings.getInstance().values());
	}

	@Override
	public @Nullable JComponent getPreferredFocusedComponent() {
		return form == null ? null : form.preferredFocus();
	}

	@Override
	public void disposeUIResources() {
		form = null;
	}
}
