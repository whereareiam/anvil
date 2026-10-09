package me.whereareiam.anvil.integration.gradle

import me.whereareiam.anvil.integration.gradle.artifact.provider.ArtifactJvmArgumentProvider
import me.whereareiam.anvil.integration.gradle.config.AnvilEngineProperties
import me.whereareiam.anvil.integration.gradle.task.AccountTask
import me.whereareiam.anvil.integration.gradle.task.ScenarioRunnerTask
import me.whereareiam.anvil.tooling.gradle.ToolingPreparationTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaToolchainService

/**
 * Installs foreground scenario execution and independent editor launch preparation without JUnit.
 *
 * Scenario definitions come only from the `anvil` source set; ordinary tests are never compiled or
 * scanned for them.
 */
class AnvilPlugin : Plugin<Project> {
	override fun apply(project: Project) {
		project.pluginManager.apply(AnvilBasePlugin::class.java)
		val extension = project.extensions.getByType(AnvilExtension::class.java)
		val inputs = ScenarioInputs(
			runtimeClasspath = project.files(extension.sourceSet.runtimeClasspath, toolingRuntime(project, extension.frameworkVersion)),
			classes = extension.sourceSet.output.classesDirs,
			resources = project.files(extension.sourceSet.output.resourcesDir),
			properties = AnvilEngineProperties.create(project, extension),
			artifacts = project.objects.listProperty(ArtifactJvmArgumentProvider::class.java).convention(emptyList()),
			classesTask = extension.sourceSet.classesTaskName,
		)
		extension.onArtifact { name, files ->
			val input = project.objects.newInstance(ArtifactJvmArgumentProvider::class.java)
			input.artifactName.set(name)
			input.artifactFiles.from(files)
			inputs.artifacts.add(input)
		}

		registerPreparation(project, inputs)
		registerRunner(project, inputs)
		registerAccount(project, inputs)
	}

	private fun registerPreparation(project: Project, inputs: ScenarioInputs) {
		project.tasks.register("anvilTooling", ToolingPreparationTask::class.java) {
			group = "anvil"
			description = "Compiles Anvil scenarios and prepares their tooling runtime."
			runtimeClasspath.from(inputs.runtimeClasspath)
			scenarioClasses.from(inputs.classes)
			scenarioResources.from(inputs.resources)
			artifacts.set(inputs.artifacts)
			jvmProperties.set(inputs.properties)
			definitionIndex.convention(project.layout.buildDirectory.dir("anvil/scenario-index"))
			launchManifest.convention(project.layout.buildDirectory.file("anvil/tooling.json"))
			dependsOn(project.tasks.named(inputs.classesTask))
			javaLauncher.convention(launcher(project))
		}
	}

	private fun registerRunner(project: Project, inputs: ScenarioInputs) {
		project.tasks.register("anvilScenario", ScenarioRunnerTask::class.java) {
			group = "anvil"
			description = "Lists discovered scenarios or starts one foreground scenario."
			javaLauncher.set(launcher(project))
			runtimeClasspath.from(inputs.runtimeClasspath)
			scenarioClasses.from(inputs.classes)
			scenarioResources.from(inputs.resources)
			artifacts.set(inputs.artifacts)
			engineProperties.set(inputs.properties)
			dependsOn(project.tasks.named(inputs.classesTask))
		}
	}

	private fun registerAccount(project: Project, inputs: ScenarioInputs) {
		project.tasks.register("anvilAccount", AccountTask::class.java) {
			group = "anvil"
			description = "Signs an account in with --login=<id> or removes it with --logout=<id>."
			javaLauncher.set(launcher(project))
			runtimeClasspath.from(inputs.runtimeClasspath)
			engineProperties.set(inputs.properties)
		}
	}

	private fun launcher(project: Project) = project.extensions.getByType(JavaToolchainService::class.java)
		.launcherFor(project.extensions.getByType(JavaPluginExtension::class.java).toolchain)

	private fun toolingRuntime(project: Project, version: String): Configuration =
		project.configurations.create("anvilToolingRuntime") {
			description = "Anvil's internal tooling runtime"
			isCanBeConsumed = false
			isCanBeResolved = true
			attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
			attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.LIBRARY))
			dependencies.add(project.dependencies.create("me.whereareiam.anvil:tooling-launcher:$version"))
			dependencies.add(project.dependencies.create("me.whereareiam.anvil:tooling-builtin:$version"))
		}

	/**
	 * Inputs shared by the scenario tasks of one project.
	 */
	private class ScenarioInputs(
		val runtimeClasspath: FileCollection,
		val classes: FileCollection,
		val resources: FileCollection,
		val properties: Provider<Map<String, String>>,
		val artifacts: ListProperty<ArtifactJvmArgumentProvider>,
		val classesTask: String,
	)
}
