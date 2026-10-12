package me.whereareiam.anvil.integration.intellij.scenario.runconfiguration;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

import org.jetbrains.annotations.NotNull;

/**
 * Registers Anvil scenario runs in the IDE's run configuration selector.
 */
public final class AnvilConfigurationType extends ConfigurationTypeBase implements DumbAware {
	private static final String ID = "AnvilScenario";

	public AnvilConfigurationType() {
		super(ID, "Anvil Scenario", "Run an Anvil scenario with interactive controls",
				IconLoader.getIcon("/icons/anvil.svg", AnvilConfigurationType.class));
		addFactory(new ConfigurationFactory(this) {
			@Override
			public @NotNull String getId() {
				return ID;
			}

			@Override
			public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
				return new AnvilRunConfiguration(project, this, "Anvil Scenario");
			}
		});
	}

	/**
	 * Returns the registered type.
	 */
	public static @NotNull AnvilConfigurationType getInstance() {
		return ConfigurationTypeUtil.findConfigurationType(AnvilConfigurationType.class);
	}

	/**
	 * Returns the factory that creates Anvil run configurations.
	 */
	public @NotNull ConfigurationFactory factory() {
		return getConfigurationFactories()[0];
	}
}
