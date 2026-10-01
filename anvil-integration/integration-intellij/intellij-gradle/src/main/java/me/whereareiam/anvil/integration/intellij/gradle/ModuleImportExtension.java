package me.whereareiam.anvil.integration.intellij.gradle;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;

import me.whereareiam.anvil.integration.intellij.gradle.model.ImportedModule;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;

/**
 * Adds module identity and Anvil preparation routes during ordinary Gradle sync.
 */
public final class ModuleImportExtension extends AbstractProjectResolverExtension {
	@Override
	public void populateModuleExtraModels(
			@NotNull IdeaModule gradleModule,
			@NotNull DataNode<ModuleData> ideModule
	) {
		ideModule.createChild(ModuleDataRegistration.KEY, describe(gradleModule.getGradleProject()));
		super.populateModuleExtraModels(gradleModule, ideModule);
	}

	private static @NotNull ImportedModule describe(@NotNull GradleProject project) {
		GradleTask preparation = project.getTasks().stream()
				.filter(task -> "anvilTooling".equals(task.getName()))
				.findFirst()
				.orElse(null);

		boolean scenarioTask = project.getTasks().stream()
				.anyMatch(task -> "anvilScenario".equals(task.getName()));
		String buildRoot = project.getProjectIdentifier().getBuildIdentifier().getRootDir().getAbsolutePath();

		return ImportedModule.builder()
				.enabled(preparation != null)
				.incompatible(scenarioTask && preparation == null)
				.moduleDirectory(project.getProjectDirectory().getAbsolutePath())
				.buildRootDirectory(buildRoot)
				.projectPath(project.getPath())
				.executionDirectory(buildRoot)
				.preparationTaskPath(preparation == null ? "" : preparation.getPath())
				.build();
	}
}
