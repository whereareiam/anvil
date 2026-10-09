import me.whereareiam.anvil.buildlogic.library.LibraryRegistrySource
import me.whereareiam.anvil.buildlogic.library.LibraryReleasesSource
import me.whereareiam.anvil.buildlogic.library.ReleaseClosuresArgument
import me.whereareiam.anvil.buildlogic.library.WriteReleaseClosures
import me.whereareiam.anvil.buildlogic.library.releaseClosureClasspath

plugins {
	java
}

// Tests of a library's worker start real workers on each release's locked closure, which Gradle resolves exactly as
// the library's release data lists it, so they run what a worker downloads without downloading it. The project
// belongs to the library whose family root folder contains it.
val (libraryId, releaseData) = providers.of(LibraryRegistrySource::class) {
	parameters.rootDirectory.set(isolated.rootProject.projectDirectory)
}.get().entries.singleOrNull { projectDir.absoluteFile.startsWith(it.value.parentFile.absoluteFile) } ?: throw GradleException(
	"$path applies release-closures, but only a project inside a library family root, such as "
		+ "anvil-protocol/protocol-mcprotocol, has release data"
)
val releases = providers.of(LibraryReleasesSource::class) {
	parameters.releasesFile.set(releaseData)
}.get()
val releaseClosures = releases.map { releaseClosureClasspath(it) }

val writeClosures = tasks.register<WriteReleaseClosures>("writeReleaseClosures") {
	description = "Writes where the locked closure of every $libraryId release lies, for worker tests."
	this.releases.set(releaseClosures)
	destination.set(layout.buildDirectory.file("release-closures/$libraryId.properties"))
}

tasks.withType<Test>().configureEach {
	dependsOn(writeClosures)
	jvmArgumentProviders.add(objects.newInstance<ReleaseClosuresArgument>().apply {
		library.set(libraryId)
		releases.set(releaseClosures)
		closures.set(writeClosures.flatMap { it.destination })
	})
}
