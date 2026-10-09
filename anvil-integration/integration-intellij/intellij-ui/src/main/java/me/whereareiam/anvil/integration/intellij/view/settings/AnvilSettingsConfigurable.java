package me.whereareiam.anvil.integration.intellij.view.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import javax.swing.JComponent;

import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Binds Settings | Tools | Anvil to its form and the IDE Apply/Reset lifecycle.
 */
public final class AnvilSettingsConfigurable implements SearchableConfigurable {
	public static final String ID = "me.whereareiam.anvil.settings";
	private final @NotNull Preferences preferences = ApplicationManager.getApplication().getService(Preferences.class);
	private @Nullable SettingsForm form;

	@Override
	public @NotNull String getId() {
		return ID;
	}

	@Override
	public @NotNull String getDisplayName() {
		return "Anvil";
	}

	@Override
	public @NotNull JComponent createComponent() {
		if (form == null) form = new SettingsForm();
		return form.getComponent();
	}

	@Override
	public boolean isModified() {
		return form != null && form.isModified();
	}

	@Override
	public void apply() throws ConfigurationException {
		if (form == null) return;
		var defaultPath = Path.of(System.getProperty("user.home"), ".anvil", "accounts")
				.toAbsolutePath().normalize();
		try {
			var path = Path.of(form.accountDirectory()).toAbsolutePath().normalize();
			var options = form.apply();
			preferences.update(options.toBuilder()
					.accountsDirectory(path.equals(defaultPath) ? "" : options.getAccountsDirectory())
					.build());
		} catch (InvalidPathException invalid) {
			throw new ConfigurationException(invalid.getMessage());
		}
		reset();
	}

	@Override
	public void reset() {
		if (form == null) return;
		form.reset(preferences.snapshot(), preferences.accountsDirectory());
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
