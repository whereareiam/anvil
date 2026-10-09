import me.whereareiam.anvil.buildlogic.platform.PlatformLayout
import me.whereareiam.anvil.buildlogic.platform.PlatformLayoutSource

// Platform version data that declares the agent a provider installs is checked against the agent's classes only by
// the module-platform-provider convention. Once the project tree is known, every project shipping such data must apply it.
gradle.settingsEvaluated {
	val directories = linkedMapOf<String, String>()
	fun collect(descriptor: ProjectDescriptor) {
		directories[descriptor.path] = descriptor.projectDir.absolutePath
		descriptor.children.forEach(::collect)
	}
	collect(rootProject)

	val platformLayout = PlatformLayout(providers.of(PlatformLayoutSource::class) {
		parameters.projectDirectories.set(directories)
	}.get())
	gradle.lifecycle.afterProject {
		platformLayout.violation(path, pluginManager::hasPlugin)?.let { throw GradleException(it) }
	}
}
