package me.whereareiam.anvil.buildlogic.data

import com.fasterxml.jackson.databind.JsonNode
import me.whereareiam.anvil.buildlogic.library.pin.Checksums
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/**
 * Pins the minecraft-data files of every release in `block-data.toml`.
 *
 * The task resolves the commit of the file's minecraft-data `version` tag and reads `data/dataPaths.json` at that
 * commit. For each row, the Minecraft versions dataPaths names must agree on their `blocks` and
 * `blockCollisionShapes` directories; a version it does not name shares them through its row, whose versions speak
 * one protocol. A row none of whose versions dataPaths names keeps no pins, and the generated data reports it as
 * missing. The task rewrites the commit and each row's pins with the files' sha256. While the commit stays the same,
 * a pin never changes: a file whose checksum differs fails the task before anything is written.
 */
@UntrackedTask(because = "It rewrites block-data.toml in place and downloads every file it pins")
abstract class PinBlockData : DefaultTask() {
	/**
	 * `block-data.toml`, read and rewritten in place.
	 */
	@get:Internal
	abstract val pins: RegularFileProperty

	@TaskAction
	fun pin() {
		val file = pins.get().asFile
		val current = BlockDataPinsFile.read(file)
		val pinned = MinecraftDataRepository(current.source.repository).use { repository ->
			val commit = repository.commitOf(current.source.version)
			val paths = repository.json(commit, "data/dataPaths.json").path("pc")
			if (!paths.isObject) throw GradleException("minecraft-data $commit has no pc entries in data/dataPaths.json")

			val releases = current.releases.map { release ->
				val directories = directories(release, paths) ?: return@map BlockDataPins.Release(release.minecraft, null, null)
				BlockDataPins.Release(
					release.minecraft,
					pin(repository, commit, "data/${directories.first}/blocks.json"),
					pin(repository, commit, "data/${directories.second}/blockCollisionShapes.json"),
				)
			}
			BlockDataPins(current.source.copy(commit = commit), releases)
		}
		if (pinned.source.commit == current.source.commit) requireUnchanged(current, pinned)

		file.writeText(BlockDataPinsFile.format(pinned))
		val missing = pinned.releases.filterNot { it.pinned }.map { it.key }
		logger.lifecycle("Pinned the block data of ${pinned.releases.count { it.pinned }} releases at minecraft-data "
			+ "${pinned.source.version} (${pinned.source.commit})" + if (missing.isEmpty()) "" else "; it has none for $missing")
	}

	private fun directories(release: BlockDataPins.Release, paths: JsonNode): Pair<String, String>? {
		val named = release.minecraft.filter { paths.has(it.toString()) }.associateWith { version ->
			val entry = paths.path(version.toString())
			entry.path("blocks").asText("") to entry.path("blockCollisionShapes").asText("")
		}
		if (named.isEmpty()) return null
		named.filterValues { it.first.isEmpty() || it.second.isEmpty() }.keys.forEach {
			throw GradleException("minecraft-data names Minecraft $it without blocks and blockCollisionShapes in data/dataPaths.json")
		}
		if (named.values.distinct().size > 1)
			throw GradleException("minecraft-data gives the Minecraft versions of release ${release.key} different block data, "
				+ "${named}; versions of one row must share a protocol and so their block registry")

		return named.values.first()
	}

	private fun pin(repository: MinecraftDataRepository, commit: String, path: String): BlockDataPins.PinnedFile =
		BlockDataPins.PinnedFile(path, Checksums.sha256(repository.read(commit, path).inputStream()))

	private fun requireUnchanged(current: BlockDataPins, pinned: BlockDataPins) {
		val changes = current.releases.zip(pinned.releases).filter { (old, new) -> old.pinned && old != new }
		if (changes.isEmpty()) return

		throw GradleException("minecraft-data ${current.source.commit} would change pins that block-data.toml already has; "
			+ "pins never change silently, so check the files and clear a pin only on purpose:\n"
			+ changes.joinToString("\n") { (old, new) -> "  release ${old.key}: ${old.blocks} ${old.collisionShapes} -> ${new.blocks} ${new.collisionShapes}" })
	}
}
