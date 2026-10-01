package me.whereareiam.anvil.integration.intellij.runconfiguration;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

import org.jetbrains.annotations.NotNull;

/**
 * Registers Anvil scenario launches in the IDE's run configuration selector.
 */
public final class AnvilConfigurationType extends ConfigurationTypeBase implements DumbAware {
	public AnvilConfigurationType() {
		super(
				"AnvilScenario",
				"Anvil Scenario",
				"Run an Anvil scenario with interactive controls",
				IconLoader.getIcon("/icons/anvil.svg", AnvilConfigurationType.class));
		addFactory(
				new ConfigurationFactory(this) {
					@Override
					public @NotNull String getId() {
						return "AnvilScenario";
					}

					@Override
					public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
						return new AnvilRunConfiguration(project, this, "Anvil Scenario");
					}
				});
	}
}
