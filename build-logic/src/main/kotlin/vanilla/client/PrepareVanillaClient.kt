package me.whereareiam.anvil.buildlogic.vanilla.client

import me.whereareiam.anvil.buildlogic.download.Checksums
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations
import java.io.File
import java.io.OutputStream
import javax.inject.Inject

/**
 * Prepares one pinned vanilla client, `prepareVanillaClient-<minecraft>`: downloads its version metadata, client JAR,
 * mappings and the libraries of the platform it runs on into the vanilla client cache, each verified by SHA-1, remaps
 * an obfuscated client to Mojang's names with the AutoRenamingTool, which strips its signature, widens the access of
 * the members its harness family's [accessLists] name ([AccessWidening]), and writes a [PreparedClient] into
 * [prepared].
 *
 * The client runs on the host when Mojang ships natives of its libraries for the host's platform, else in a
 * [ClientPlatform.CONTAINER], or always there when [container] is set. A remapped or widened client is kept in the
 * cache with what it was made from, the checksums of its inputs and the access lists' content, and made again only
 * when that changes.
 */
@UntrackedTask(because = "It verifies its cached downloads itself and fills a cache outside the build")
abstract class PrepareVanillaClient : DefaultTask() {
	/**
	 * The pinned release.
	 */
	@get:Internal
	abstract val release: Property<VanillaClientPins.Release>

	/**
	 * The vanilla client cache, `~/.anvil/vanilla-clients` unless the build names another.
	 */
	@get:Internal
	abstract val cache: DirectoryProperty

	/**
	 * The AutoRenamingTool's class path.
	 */
	@get:Internal
	abstract val remapper: ConfigurableFileCollection

	/**
	 * The `accesstransformer.cfg` files of the release's harness family and of the release itself, those that exist,
	 * whose members the prepared client widens.
	 */
	@get:Internal
	abstract val accessLists: ConfigurableFileCollection

	/**
	 * Whether the client runs in a container even where the host could run it.
	 */
	@get:Internal
	abstract val container: Property<Boolean>

	/**
	 * Where the [PreparedClient] is written.
	 */
	@get:OutputDirectory
	abstract val prepared: DirectoryProperty

	@get:Inject
	protected abstract val execOperations: ExecOperations

	@TaskAction
	fun prepare() {
		val release = release.get()
		if (!release.pinned)
			throw GradleException("${VanillaClientPinsFile.NAME} does not pin Minecraft ${release.minecraft} yet; run pinVanillaClients")
		val root = cache.get().asFile
		val directory = File(root, release.minecraft)
		VanillaFileStore().use { store ->
			val metadataFile = store.fetch(release.metadata!!.url, release.metadata.sha1, File(directory, "version.json"))
			val metadata = VersionMetadata.read(metadataFile)
			requireMatches(release, metadata)

			val client = store.fetch(metadata.client.url, metadata.client.sha1, File(directory, metadata.client.path))
			val host = ClientPlatform.host()
			val platform = if (!container.get() && metadata.runsOn(host)) host else ClientPlatform.CONTAINER
			if (!metadata.runsOn(platform))
				throw GradleException("Minecraft ${release.minecraft} ships no natives for $platform")
			val libraries = metadata.libraries(platform).map { store.fetch(it.url, it.sha1, File(root, "libraries/${it.path}")) }
			val named = metadata.mappings?.let { mappings ->
				remap(client, store.fetch(mappings.url, mappings.sha1, File(directory, mappings.path)), libraries, File(directory, "client-named.jar"))
			} ?: client
			val widened = widen(named, File(directory, "client-widened.jar"))

			PreparedClient(platform, libraries + widened).write(prepared.get().asFile)
			logger.lifecycle("Prepared the vanilla client of Minecraft ${release.minecraft} to run on $platform")
		}
	}

	private fun requireMatches(release: VanillaClientPins.Release, metadata: VersionMetadata) {
		if (metadata.minecraft != release.minecraft)
			throw GradleException("The metadata pinned for Minecraft ${release.minecraft} describes ${metadata.minecraft}")
		if (metadata.client.sha1 != release.client!!.sha1 || metadata.mappings?.sha1 != release.mappings?.sha1)
			throw GradleException("The metadata pinned for Minecraft ${release.minecraft} names another client or mappings than "
				+ "${VanillaClientPinsFile.NAME} pins; run pinVanillaClients")
	}

	/**
	 * Remaps [client] to Mojang's names with [mappings] into [target], unless [target] was made from the same files.
	 */
	private fun remap(client: File, mappings: File, libraries: List<File>, target: File): File {
		val marker = File(target.parentFile, "${target.name}.from")
		val source = "client ${Checksums.sha1(client)}\nmappings ${Checksums.sha1(mappings)}\nremapper ${remapper.files.joinToString { it.name }}\n"
		if (target.isFile && marker.isFile && marker.readText() == source) return target

		val partial = File(target.parentFile, "${target.name}.part")
		execOperations.javaexec {
			classpath(remapper)
			mainClass.set("net.neoforged.art.Main")
			args("--input", client.absolutePath, "--output", partial.absolutePath, "--names", mappings.absolutePath, "--reverse",
				"--strip-sigs", "ALL")
			libraries.forEach { args("--lib", it.absolutePath) }
			standardOutput = OutputStream.nullOutputStream()
		}
		replace(partial, target)
		marker.writeText(source)
		return target
	}

	/**
	 * Widens the access the [accessLists] name in [client] into [target], unless [target] was made from the same client
	 * and the same lists.
	 */
	private fun widen(client: File, target: File): File {
		val lists = accessLists.files.filter(File::isFile)
		val text = lists.joinToString("") { "# ${it.parentFile.name}/${it.name}\n${it.readText()}\n" }
		val marker = File(target.parentFile, "${target.name}.from")
		val source = "client ${Checksums.sha1(client)}\naccess ${Checksums.sha1(text.byteInputStream())}\nwidening $WIDENING_VERSION\n"
		if (target.isFile && marker.isFile && marker.readText() == source) return target

		val list = lists.map { AccessList.parse(it.readText(), "${it.parentFile.name}/${it.name}") }.fold(AccessList(listOf()), AccessList::plus)
		val partial = File(target.parentFile, "${target.name}.part")
		AccessWidening(list).widen(client, partial)
		replace(partial, target)
		marker.writeText(source)
		return target
	}

	private fun replace(partial: File, target: File) {
		if (!partial.renameTo(target) && !(target.delete() && partial.renameTo(target)))
			throw GradleException("Could not move ${partial.name} to $target")
	}

	private companion object {
		/**
		 * The version of how [AccessWidening] writes a client, raised whenever its output changes for the same input.
		 */
		const val WIDENING_VERSION = 1
	}
}
