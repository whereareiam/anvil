package me.whereareiam.anvil.buildlogic.module

/**
 * The one named exception to "implementations depend only on APIs": a shared implementation, a project applying
 * `module-shared-implementation`, holds code that several sibling families embed, such as the library-neutral native
 * worker that every protocol library runs. An implementation compiles against it through its `sharedImplementation`
 * configuration, which the toolkit's architecture verifier does not read, so these rules confine it instead:
 *
 * - only a shared implementation may be the target of a `sharedImplementation` dependency;
 * - only an implementation may declare one, never an API;
 * - the declaring project lies below the shared implementation's parent, the family group it serves, and outside
 *   the shared implementation itself;
 * - a shared implementation is no API and declares no `sharedImplementation` dependency of its own.
 *
 * The shared implementation itself remains an ordinary implementation for the toolkit, which verifies that it
 * depends only on APIs, and assemblies embed it like any implementation.
 */
object SharedImplementations {
	/**
	 * The name of the consumer configuration.
	 */
	const val configuration = "sharedImplementation"

	/**
	 * The convention a shared implementation applies.
	 */
	const val convention = "module-shared-implementation"

	/**
	 * One project of the build, as the rules see it.
	 *
	 * @property path project path, such as `:anvil-protocol:protocol-worker`
	 * @property shared whether the project applies [convention]
	 * @property api whether the project is an API
	 * @property dependencies paths of the projects its [configuration] configuration names
	 */
	data class Project(val path: String, val shared: Boolean, val api: Boolean, val dependencies: Collection<String>)

	/**
	 * Explains every way [projects] break the rules.
	 *
	 * @return violations in project order, empty when the build follows the rules
	 */
	fun violations(projects: Collection<Project>): List<String> {
		val byPath = projects.associateBy { it.path }
		return buildList {
			for (project in projects) {
				if (project.shared && project.api)
					add("${project.path} applies $convention, but an API is no shared implementation")
				if (project.shared && parent(project.path).isEmpty())
					add("${project.path} applies $convention, but a shared implementation serves the families of its parent and must not be a root project")
				if (project.dependencies.isEmpty()) continue
				if (project.shared)
					add("${project.path} is a shared implementation and must not declare $configuration dependencies itself")
				if (project.api)
					add("${project.path}:$configuration is declared by an API, which may depend only on APIs")

				for (path in project.dependencies.sorted()) {
					val target = byPath[path]
					if (target == null || !target.shared) {
						add("${project.path}:$configuration -> $path, which is no shared implementation; it must apply id(\"$convention\")")
						continue
					}

					if (project.path.startsWith("$path:")) {
						add("${project.path}:$configuration -> $path names the shared implementation that contains it")
						continue
					}

					val group = parent(path)
					if (!project.path.startsWith("$group:"))
						add("${project.path}:$configuration -> $path lies outside $group, the group the shared implementation serves")
				}
			}
		}
	}

	private fun parent(path: String): String = path.substringBeforeLast(':')
}
