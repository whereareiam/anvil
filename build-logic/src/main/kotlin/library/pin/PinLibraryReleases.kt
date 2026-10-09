package me.whereareiam.anvil.buildlogic.library.pin

import me.whereareiam.anvil.buildlogic.library.ArtifactCoordinate
import me.whereareiam.anvil.buildlogic.library.LibraryRelease
import me.whereareiam.anvil.buildlogic.library.LibraryReleaseReader
import me.whereareiam.anvil.buildlogic.library.ReleaseArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File
import java.net.URI

/**
 * Pins the worker runtime closure of every release in a library's releases file.
 *
 * For each release, Gradle resolves its `module` at runtime scope; the task rewrites that release's
 * `[[release.artifact]]` tables in classpath order with each JAR's module (with its classifier), the URL of the
 * first library repository that serves the same bytes, and its sha256. It keeps the hand-written release fields
 * and comments, and it never changes an existing non-empty pin: a JAR whose checksum differs from its pin anywhere
 * in the file fails the task before anything is downloaded or written.
 */
@UntrackedTask(because = "It rewrites the releases file in place and downloads every JAR it pins")
abstract class PinLibraryReleases : DefaultTask() {
	/**
	 * The library's `<id>-releases.toml`, read and rewritten in place.
	 */
	@get:Internal
	abstract val releasesFile: RegularFileProperty

	/**
	 * Root URLs of the library repositories the settings declare, in search order.
	 */
	@get:Input
	abstract val repositories: ListProperty<String>

	/**
	 * The closure Gradle resolved for each release.
	 */
	@get:Nested
	abstract val releases: ListProperty<ResolvedRelease>

	@TaskAction
	fun pin() {
		val file = releasesFile.get().asFile
		val current = LibraryReleaseReader.read(file)
		val resolved = releases.get().associate { it.version.get() to it.artifacts.get() }
		val jars = current.associate { release ->
			val artifacts = resolved[release.version] ?: throw GradleException("${file.name}: release ${release.version} was not resolved")
			release.version to artifacts.map { ResolvedJar.of(release, it) }
		}
		requireExistingPins(file, current, jars.values.flatten())

		val closures = RepositoryProbe(repositories.get().map(::URI)).use { probe ->
			jars.mapValues { (_, release) ->
				release.map { ReleaseArtifact(it.coordinate.module, probe.locate(it.coordinate.repositoryPath, it.sha256), it.sha256) }
			}
		}
		val text = ReleaseArtifactTables.replace(file.readText(), closures)
		requireOnlyClosuresChanged(file, current, text, closures)
		file.writeText(text)

		val pinned = closures.values.sumOf { it.size }
		val previous = current.flatMap(LibraryRelease::artifacts).map { it.module to it.sha256 }.toSet()
		val changed = closures.values.flatten().count { (it.module to it.sha256) !in previous }
		logger.lifecycle("Pinned $pinned JARs of ${closures.size} releases in ${file.name}; $changed pins are new")
	}

	private fun requireExistingPins(file: File, current: List<LibraryRelease>, jars: List<ResolvedJar>) {
		val pins = current.flatMap(LibraryRelease::artifacts).filter { it.sha256.isNotEmpty() }.groupBy({ it.module }, { it.sha256 })
		val conflicts = jars.distinctBy { it.coordinate.module to it.sha256 }.flatMap { jar ->
			pins[jar.coordinate.module].orEmpty().distinct().filter { it != jar.sha256 }
				.map { "${jar.coordinate.module} is pinned to $it, but resolves to ${jar.sha256}" }
		}
		if (conflicts.isNotEmpty())
			throw GradleException("${file.name} pins checksums that the resolved JARs do not match; pins never change "
				+ "silently, so check the JARs and clear a pin only on purpose:\n" + conflicts.joinToString("\n") { "  $it" })
	}

	private fun requireOnlyClosuresChanged(file: File, current: List<LibraryRelease>, text: String, closures: Map<String, List<ReleaseArtifact>>) {
		val candidate = File(temporaryDir, file.name).apply { writeText(text) }
		val rewritten = LibraryReleaseReader.read(candidate)
		if (rewritten.map { it.copy(artifacts = emptyList()) } != current.map { it.copy(artifacts = emptyList()) })
			throw GradleException("Pinning ${file.name} would change its release fields; nothing was written")
		if (rewritten.associate { it.version to it.artifacts } != closures)
			throw GradleException("Pinning ${file.name} would not write the resolved closures; nothing was written")
	}

	/**
	 * One JAR of a release closure, identified and hashed.
	 */
	private class ResolvedJar(val coordinate: ArtifactCoordinate, val sha256: String) {
		companion object {
			fun of(release: LibraryRelease, artifact: ResolvedArtifactResult): ResolvedJar {
				val component = artifact.id.componentIdentifier as? ModuleComponentIdentifier ?: throw GradleException(
					"Release ${release.version} resolves ${artifact.id.componentIdentifier.displayName}, which is not a published module"
				)
				val coordinate = ArtifactCoordinate.of(component.group, component.module, component.version, artifact.file.name)
				return ResolvedJar(coordinate, Checksums.sha256(artifact.file))
			}
		}
	}
}
