package me.whereareiam.anvil.buildlogic.minecraftdata.pin

import com.fasterxml.jackson.databind.JsonNode
import me.whereareiam.anvil.buildlogic.minecraftdata.MinecraftDataKind
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/**
 * Pins the minecraft-data files of every release in one kind's pins file, such as `pinBlockData` for
 * `block-data.toml`.
 *
 * The task resolves the commit of the file's minecraft-data `version` tag and reads `data/pc/common/protocolVersions.json`
 * and `data/dataPaths.json` at that commit. For each row, the Minecraft versions minecraft-data gives the row's
 * protocol must agree on the directories dataPaths names for the kind's entries; a version dataPaths does not name
 * shares them through its protocol. A row none of whose versions dataPaths names keeps no pins, and the generated data
 * reports it as missing. The task rewrites the commit and each row's pins with the files' sha256. While the commit
 * stays the same, a pin never changes: a file whose checksum differs fails the task before anything is written.
 */
@UntrackedTask(because = "It rewrites the pins file in place and downloads every file it pins")
abstract class PinMinecraftData : DefaultTask() {
	/**
	 * The kind whose pins the task writes.
	 */
	@get:Internal
	abstract val kind: Property<MinecraftDataKind>

	/**
	 * The kind's pins file, read and rewritten in place.
	 */
	@get:Internal
	abstract val pins: RegularFileProperty

	@TaskAction
	fun pin() {
		val kind = kind.get()
		val pinsFile = MinecraftDataPinsFile(kind)
		val file = pins.get().asFile
		val current = pinsFile.read(file)
		val pinned = MinecraftDataRepository(current.source.repository).use { repository ->
			val commit = repository.commitOf(current.source.version)
			val paths = repository.json(commit, DATA_PATHS).path("pc")
			if (!paths.isObject) throw GradleException("minecraft-data $commit has no pc entries in $DATA_PATHS")
			val protocols = repository.json(commit, PROTOCOL_VERSIONS)
			if (!protocols.isArray) throw GradleException("minecraft-data $commit has no array in $PROTOCOL_VERSIONS")

			val releases = current.releases.map { release ->
				val directories = directories(kind, release.protocol, versions(protocols, release.protocol), paths)
					?: return@map MinecraftDataPins.Release(release.protocol, emptyMap())
				MinecraftDataPins.Release(release.protocol, kind.files.associate { file ->
					val path = "data/${directories.getValue(file.entry)}/${file.entry}.json"
					file.field to MinecraftDataPins.PinnedFile(path, repository.sha256(commit, path))
				})
			}
			MinecraftDataPins(current.source.copy(commit = commit), releases)
		}
		if (pinned.source.commit == current.source.commit) requireUnchanged(kind, current, pinned)

		file.writeText(pinsFile.format(pinned, path))
		val missing = pinned.releases.filterNot { it.pinned }.map { it.protocol }
		logger.lifecycle("Pinned the ${kind.subject} of ${pinned.releases.count { it.pinned }} releases at minecraft-data "
			+ "${pinned.source.version} (${pinned.source.commit})" + if (missing.isEmpty()) "" else "; it has none for protocols $missing")
	}

	/**
	 * The Minecraft versions minecraft-data gives [protocol], snapshots included, whose protocol numbers differ.
	 */
	private fun versions(protocols: JsonNode, protocol: Int): List<String> =
		protocols.filter { it.path("version").asInt(-1) == protocol }.map { it.path("minecraftVersion").asText() }

	/**
	 * The directory of each of the kind's entries that the named versions of [protocol] share, or `null` when
	 * dataPaths names none of them.
	 */
	private fun directories(kind: MinecraftDataKind, protocol: Int, versions: List<String>, paths: JsonNode): Map<String, String>? {
		val entries = kind.files.map { it.entry }
		val named = versions.filter(paths::has).associateWith { version ->
			val entry = paths.path(version)
			entries.associateWith { entry.path(it).asText("") }
		}
		if (named.isEmpty()) return null
		named.filterValues { directories -> directories.values.any { it.isEmpty() } }.keys.forEach {
			throw GradleException("minecraft-data names Minecraft $it without ${entries.joinToString(" and ")} in $DATA_PATHS")
		}
		if (named.values.distinct().size > 1)
			throw GradleException("minecraft-data gives the Minecraft versions of protocol $protocol different "
				+ "${kind.subject}, $named; versions of one protocol must share it")

		return named.values.first()
	}

	private fun requireUnchanged(kind: MinecraftDataKind, current: MinecraftDataPins, pinned: MinecraftDataPins) {
		val changes = current.releases.zip(pinned.releases).filter { (old, new) -> old.pinned && old != new }
		if (changes.isEmpty()) return

		throw GradleException("minecraft-data ${current.source.commit} would change pins that ${kind.pinsFile} already has; "
			+ "pins never change silently, so check the files and clear a pin only on purpose:\n"
			+ changes.joinToString("\n") { (old, new) -> "  protocol ${old.protocol}: ${old.files.values} -> ${new.files.values}" })
	}

	private companion object {
		const val DATA_PATHS = "data/dataPaths.json"
		const val PROTOCOL_VERSIONS = "data/pc/common/protocolVersions.json"
	}
}
