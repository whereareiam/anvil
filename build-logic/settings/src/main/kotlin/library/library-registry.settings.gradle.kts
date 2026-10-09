import me.whereareiam.anvil.buildlogic.library.LibraryLayout
import me.whereareiam.anvil.buildlogic.library.LibraryRegistrySource
import me.whereareiam.anvil.buildlogic.library.LibraryRepositories

// The build's protocol libraries. The settings declare the repositories their releases download from as data, and
// projects read them through the build's extension, since they cannot read the settings. Once the project tree is
// known, every library family root and library side folder is checked for the convention it must apply.
val libraryRepositories = extensions.create<LibraryRepositories>("libraryRepositories")
gradle.extensions.add(LibraryRepositories::class.java, "libraryRepositories", libraryRepositories)

val libraries = providers.of(LibraryRegistrySource::class) {
	parameters.rootDirectory.set(layout.rootDirectory)
}

gradle.settingsEvaluated {
	libraryRepositories.urls.finalizeValue()

	val projects = mutableListOf<LibraryLayout.Project>()
	fun collect(descriptor: ProjectDescriptor) {
		projects += LibraryLayout.Project(descriptor.path, descriptor.projectDir, descriptor.children.map { it.name })
		descriptor.children.forEach(::collect)
	}
	collect(rootProject)

	val libraryLayout = LibraryLayout.of(projects, libraries.get())
	gradle.lifecycle.afterProject {
		libraryLayout.violation(path, pluginManager::hasPlugin)?.let { throw GradleException(it) }
	}
}
