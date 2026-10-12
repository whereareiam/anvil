package me.whereareiam.anvil.integration.intellij.scenario.runconfiguration;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.scenario.editor.ScenarioDefinitions;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Creates an Anvil run configuration from a scenario definition class in the editor or the project view.
 */
public final class ScenarioConfigurationProducer extends LazyRunConfigurationProducer<AnvilRunConfiguration> {
	@Override
	public @NotNull ConfigurationFactory getConfigurationFactory() {
		return AnvilConfigurationType.getInstance().factory();
	}

	@Override
	protected boolean setupConfigurationFromContext(
			@NotNull AnvilRunConfiguration configuration,
			@NotNull ConfigurationContext context,
			@NotNull Ref<PsiElement> sourceElement
	) {
		PsiClass definition = ScenarioDefinitions.containing(context.getPsiLocation());
		if (definition == null) return false;

		ScenarioSource source = ScenarioDefinitions.source(definition);
		if (source == null) return false;

		sourceElement.set(definition);
		configuration.setSourceId(source.getId());
		configuration.setDefinition(definition.getQualifiedName());
		configuration.setProcessId("");
		configuration.setName(name(configuration, source, definition));

		return true;
	}

	@Override
	public boolean isConfigurationFromContext(@NotNull AnvilRunConfiguration configuration, @NotNull ConfigurationContext context) {
		PsiClass definition = ScenarioDefinitions.containing(context.getPsiLocation());
		if (definition == null || !configuration.getProcessId().isBlank()) return false;

		ScenarioSource source = ScenarioDefinitions.source(definition);
		return source != null
				&& source.getId().equals(configuration.getSourceId())
				&& configuration.getDefinition().equals(definition.getQualifiedName());
	}

	private static @NotNull String name(
			@NotNull AnvilRunConfiguration configuration,
			@NotNull ScenarioSource source,
			@NotNull PsiClass definition
	) {
		var catalog = ScenarioCatalog.getInstance(configuration.getProject()).snapshot();
		ScenarioDescriptor loaded = catalog.isFor(source) ? catalog.find(definition.getQualifiedName()) : null;

		return loaded != null ? loaded.getDisplayName() : String.valueOf(definition.getName());
	}
}
