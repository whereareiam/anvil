import me.whereareiam.anvil.buildlogic.module.SharedImplementations
import me.whereareiam.toolkit.architecture.model.ArchitectureExtension
import me.whereareiam.toolkit.architecture.type.ArchitectureKind

// Applied by the root project next to the toolkit's architecture plugin, whose verifyArchitecture task it extends: it
// verifies the shared implementations the build declares, the one exception to implementations depending only on APIs.
val verification = tasks.register("verifySharedImplementations") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Verifies that only implementations of a shared implementation's group compile against it."

	doLast {
		val violations = inputs.properties["violations"] as List<*>
		if (violations.isEmpty()) return@doLast

		throw GradleException(buildString {
			appendLine("Shared implementation rules were violated:")
			violations.forEach { appendLine(" - $it") }
		}.trimEnd())
	}
}

gradle.projectsEvaluated {
	val projects = rootProject.allprojects.map { project ->
		val kind = project.extensions.findByType<ArchitectureExtension>()?.kind
		SharedImplementations.Project(
			path = project.path,
			shared = project.pluginManager.hasPlugin(SharedImplementations.convention),
			api = kind == ArchitectureKind.API || (kind == null && (project.name == "api" || project.name.endsWith("-api"))),
			dependencies = project.configurations.findByName(SharedImplementations.configuration)
				?.dependencies?.withType<ProjectDependency>()?.map { it.path }.orEmpty(),
		)
	}
	verification.configure {
		inputs.property("violations", SharedImplementations.violations(projects))
	}
}

tasks.named("verifyArchitecture") {
	dependsOn(verification)
}
