import me.whereareiam.anvil.buildlogic.library.LibraryRegistrySource
import me.whereareiam.anvil.buildlogic.library.LibraryReleasesSource
import me.whereareiam.anvil.buildlogic.library.LibraryRepositories
import me.whereareiam.anvil.buildlogic.library.pin.PinLibraryReleases
import me.whereareiam.anvil.buildlogic.library.pin.ResolvedRelease
import me.whereareiam.anvil.buildlogic.library.releaseModule

// The Java ecosystem's attribute rules let each release's module select the runtime JARs of published modules.
plugins {
	`jvm-ecosystem`
}

// A protocol library family root owns <id>-releases.toml and pins it: pinLibraryReleases resolves each release's
// module and rewrites the release's [[release.artifact]] lock tables. The settings own the library repositories,
// which the library-registry settings plugin exposes; it also fails a family root that does not apply this.
val releaseData = providers.of(LibraryRegistrySource::class) {
	parameters.rootDirectory.set(isolated.rootProject.projectDirectory)
}.get().values.singleOrNull { it.parentFile.absoluteFile == projectDir.absoluteFile } ?: throw GradleException(
	"$path applies library-releases, but only a library family root that owns its <id>-releases.toml, such as "
		+ "anvil-protocol/protocol-mcprotocol/mcprotocol-releases.toml, has release data to pin"
)
val libraryRepositories = gradle.extensions.findByType<LibraryRepositories>() ?: throw GradleException(
	"$path applies library-releases, which needs the library repositories: add id(\"library-registry\") to the settings plugins"
)
val releases = providers.of(LibraryReleasesSource::class) {
	parameters.releasesFile.set(releaseData)
}.get()
val modules = releases.map { release ->
	objects.newInstance<ResolvedRelease>().apply {
		version.set(release.version)
		artifacts.set(releaseModule(release).flatMap { it.incoming.artifacts.resolvedArtifacts })
	}
}

tasks.register<PinLibraryReleases>("pinLibraryReleases") {
	group = "library"
	description = "Resolves every release in ${releaseData.name} and pins its runtime closure: modules, repository URLs and sha256."
	releasesFile.set(releaseData)
	repositories.set(libraryRepositories.urls)
	this.releases.set(modules)
}
