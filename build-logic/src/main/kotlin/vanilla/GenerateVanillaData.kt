package me.whereareiam.anvil.buildlogic.vanilla

import me.whereareiam.anvil.buildlogic.vanilla.client.ClientPlatform
import me.whereareiam.anvil.buildlogic.vanilla.client.PreparedClient
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

/**
 * Runs the vanilla client of each selected release headless and writes what the harness reads from it,
 * `generateVanillaData`: the per-tick trace of every course, `<traces>/<minecraft>/<course>.trace`, and the release's
 * block data, `<blocks>/<protocol>.blocks`, which `protocol-java-block` generates its block data from.
 *
 * Each release runs where its prepared client does. On the host, the client runs on the toolchain of the Java the live
 * matrix runs the release's server on. In a container, it runs for `linux/amd64`, whose natives every release ships,
 * in an image the task builds from [containerBase] with the X11 client library that the input classes of every client
 * load GLFW's natives against, without a display; the class path and courses are mounted read-only at their own
 * paths and only the release's work directory is writable, and the container has no network. Either way the client
 * runs in the release's work directory, where it writes its logs. The task writes each release's output into the build
 * directory first and replaces the committed data only once the release's every output was written, so a failed run
 * leaves the committed data as it was.
 */
@UntrackedTask(because = "It is a manual task that replaces the committed reference data")
abstract class GenerateVanillaData : DefaultTask() {
	/**
	 * The releases to run.
	 */
	@get:Internal
	abstract val runs: ListProperty<HarnessRun>

	/**
	 * Selected releases that `vanilla-clients.toml` does not list or has not pinned, which fail the task.
	 */
	@get:Internal
	abstract val unpinned: ListProperty<String>

	/**
	 * The courses directory.
	 */
	@get:Internal
	abstract val courses: DirectoryProperty

	/**
	 * The traces directory, one subdirectory per release.
	 */
	@get:Internal
	abstract val traces: DirectoryProperty

	/**
	 * The block data directory, one `<protocol>.blocks` per release.
	 */
	@get:Internal
	abstract val blocks: DirectoryProperty

	/**
	 * Where each release's output is written before it replaces the committed data.
	 */
	@get:Internal
	abstract val work: DirectoryProperty

	/**
	 * The vanilla client cache, which a container mounts read-only.
	 */
	@get:Internal
	abstract val cache: DirectoryProperty

	/**
	 * The image a container's image is built from for a Java feature version, `%d` standing for it.
	 */
	@get:Internal
	abstract val containerBase: Property<String>

	@get:Inject
	protected abstract val execOperations: ExecOperations

	@get:Inject
	protected abstract val fileSystemOperations: FileSystemOperations

	@get:Inject
	protected abstract val toolchains: JavaToolchainService

	@TaskAction
	fun generate() {
		if (unpinned.get().isNotEmpty())
			throw GradleException("vanilla-clients.toml does not pin Minecraft ${unpinned.get().joinToString()}; add the rows and run pinVanillaClients")
		val courseFiles = courses.get().asFile.listFiles { file -> file.name.endsWith(".toml") }.orEmpty().sortedBy { it.name }
		if (courseFiles.isEmpty()) throw GradleException("${courses.get().asFile} holds no course")

		for (run in runs.get()) {
			val directory = work.get().dir(run.minecraft).asFile
			directory.deleteRecursively()
			val output = File(directory, "traces").apply { mkdirs() }
			val blockOutput = File(directory, "blocks").apply { mkdirs() }
			val client = PreparedClient.read(run.prepared)
			val arguments = listOf(run.minecraft, "--traces", courses.get().asFile.absolutePath, output.absolutePath,
				"--blocks", blockOutput.absolutePath)
			if (client.platform == ClientPlatform.CONTAINER && client.platform != ClientPlatform.host())
				runInContainer(run, client, arguments, directory)
			else
				runOnHost(run, client, arguments, directory)

			val written = output.listFiles().orEmpty().map { it.name }.toSortedSet()
			val expected = courseFiles.filter { CourseSince.runsOn(it, run.minecraft) }.map { "${it.name.removeSuffix(".toml")}.trace" }.toSortedSet()
			if (written != expected)
				throw GradleException("The harness of Minecraft ${run.minecraft} wrote $written, expected $expected")
			val blockData = blockOutput.listFiles().orEmpty().toList()
			if (blockData.size != 1 || !blockData.single().name.endsWith(BLOCK_DATA_SUFFIX))
				throw GradleException("The harness of Minecraft ${run.minecraft} wrote ${blockData.map { it.name }}, expected one " +
					"<protocol>$BLOCK_DATA_SUFFIX")
			fileSystemOperations.sync {
				from(output)
				into(traces.get().dir(run.minecraft))
			}
			fileSystemOperations.copy {
				from(blockData.single())
				into(blocks)
			}
			logger.lifecycle("Wrote ${written.size} traces and the block data ${blockData.single().name} of Minecraft " +
				"${run.minecraft} (${client.platform})")
		}
	}

	private fun runOnHost(run: HarnessRun, client: PreparedClient, arguments: List<String>, directory: File) {
		val launcher = toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(run.java)) }.get()
		execOperations.javaexec {
			workingDir = directory
			executable = launcher.executablePath.asFile.absolutePath
			classpath(run.harness + client.classpath)
			mainClass.set(run.mainClass)
			args(arguments)
			jvmArgs("-Djava.awt.headless=true")
		}
	}

	private fun runInContainer(run: HarnessRun, client: PreparedClient, arguments: List<String>, directory: File) {
		val image = containerImage(run.java)
		val classpath = run.harness + client.classpath
		val cacheRoot = cache.get().asFile.absoluteFile
		val readOnly = (classpath.map { mountOf(it.absoluteFile, cacheRoot) } + courses.get().asFile.absoluteFile).distinct()
		val owner = Files.getAttribute(directory.toPath(), "unix:uid")
		val group = Files.getAttribute(directory.toPath(), "unix:gid")
		execOperations.exec {
			executable = "docker"
			args("run", "--rm", "--platform", PLATFORM, "--network", "none", "--hostname", "localhost", "--user", "$owner:$group", "--workdir", directory.absolutePath)
			readOnly.forEach { args("--volume", "${it.path}:${it.path}:ro") }
			args("--volume", "${directory.absolutePath}:${directory.absolutePath}")
			args(image, "java", "-Djava.awt.headless=true", "-cp", classpath.joinToString(":") { it.absolutePath }, run.mainClass)
			args(arguments)
		}
	}

	/**
	 * Builds the image a client runs in on Java [java], which Docker keeps and builds again only when its base changed.
	 */
	private fun containerImage(java: Int): String {
		val image = "anvil-vanilla-client:$java"
		val dockerfile = """
			FROM ${containerBase.get().format(java)}
			RUN apt-get update && apt-get install --yes --no-install-recommends libx11-6 && rm -rf /var/lib/apt/lists/*
		""".trimIndent()
		execOperations.exec {
			executable = "docker"
			args("build", "--quiet", "--platform", PLATFORM, "--tag", image, "-")
			standardInput = dockerfile.byteInputStream()
		}
		return image
	}

	/**
	 * The directory a container mounts for a class path [entry]: the cache for what it holds, else the entry's own
	 * directory, or the entry when it is one.
	 */
	private fun mountOf(entry: File, cacheRoot: File): File = when {
		entry.startsWith(cacheRoot) -> cacheRoot
		entry.isDirectory -> entry
		else -> entry.parentFile
	}

	private companion object {
		const val PLATFORM = "linux/amd64"
		const val BLOCK_DATA_SUFFIX = ".blocks"
	}
}
