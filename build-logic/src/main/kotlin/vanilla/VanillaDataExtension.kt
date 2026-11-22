package me.whereareiam.anvil.buildlogic.vanilla

import me.whereareiam.anvil.buildlogic.matrix.LiveMatrix
import me.whereareiam.anvil.buildlogic.module.catalog
import me.whereareiam.anvil.buildlogic.module.library
import me.whereareiam.anvil.buildlogic.vanilla.client.AccessList
import me.whereareiam.anvil.buildlogic.vanilla.client.PinVanillaClients
import me.whereareiam.anvil.buildlogic.vanilla.client.PrepareVanillaClient
import me.whereareiam.anvil.buildlogic.vanilla.client.PreparedClient
import me.whereareiam.anvil.buildlogic.vanilla.client.VanillaClientPins
import me.whereareiam.anvil.buildlogic.vanilla.client.VanillaClientPinsFile
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import java.io.File
import javax.inject.Inject

/**
 * The `vanillaData` extension of the `vanilla-harness` convention, which the standalone harness build of
 * `anvil-testkit/physics/harness` applies: it wires the generation of reference data from the real vanilla client.
 *
 * <pre>
 * vanillaData.generate(
 *     liveMatrix = layout.projectDirectory.file("../../../live-matrix.toml"),
 *     pins = layout.projectDirectory.file("vanilla-clients.toml"),
 *     courses = layout.projectDirectory.dir("../courses"),
 *     traces = layout.projectDirectory.dir("../traces"),
 *     blocks = layout.projectDirectory.dir("../../../anvil-edition/edition-java/java-data/data-block/vanilla"),
 * )
 * </pre>
 *
 * Each prepared client has the access of the members its family's `src/<family>/accesstransformer.cfg` and its
 * release's `src/<family>/release-<minecraft>/accesstransformer.cfg` name widened ([AccessList]), so the harness uses
 * them as plain Java. A family's source set compiles `src/<family>/java`, the code its releases share, against every
 * release of the family. Where the releases' members differ, each release brings its own version of the classes that reach them in
 * `src/<family>/release-<minecraft>/java`, compiled only against its own client, so every call is checked against the
 * client it runs on; the family's source set itself adds the newest release's directory.
 */
abstract class VanillaDataExtension @Inject constructor(private val project: Project) {
	@get:Inject
	protected abstract val toolchains: JavaToolchainService

	/**
	 * Wires, for every Paper row of [liveMatrix] that [pins] lists, `prepareVanillaClient-<minecraft>` and
	 * `compileHarness-<minecraft>`, which compiles the row's family sources and its own release sources against that
	 * client; one source set per family that [pins] names, compiled against the family's newest client;
	 * `pinVanillaClients`; and `generateVanillaData`, which runs the selected releases and writes the traces of the
	 * [courses] into [traces] and each release's block data into [blocks].
	 */
	fun generate(liveMatrix: RegularFile, pins: RegularFile, courses: Directory, traces: Directory, blocks: Directory) = with(project) {
		val pinned = VanillaClientPinsFile.read(pins.asFile)
		val matrix = LiveMatrix.read(liveMatrix.asFile).verified(PAPER)
		val selected = ReleaseSelection.select(matrix, providers.gradleProperty(ReleaseSelection.PROPERTY).orNull)
		val cache = layout.dir(providers.gradleProperty(CACHE_PROPERTY).orElse(providers.systemProperty("user.home").map { "$it/.anvil/vanilla-clients" })
			.map(::File))
		val container = providers.gradleProperty(CONTAINER_PROPERTY).map(String::toBoolean).orElse(false)

		val remapper = configurations.create("vanillaRemapper") {
			description = "The AutoRenamingTool, which remaps obfuscated vanilla clients to Mojang's names."
			isCanBeConsumed = false
			isTransitive = false
		}
		dependencies.addProvider(remapper.name, catalog.library("auto-renaming-tool")
			.map { "${it.module.group}:${it.module.name}:${it.versionConstraint.requiredVersion}:all" })

		// The shared classes run on the oldest release's Java, every family on its own; one compiler, the newest Java
		// any release runs on, compiles them all for their release.
		val java = extensions.getByType<JavaPluginExtension>()
		java.toolchain.languageVersion.set(JavaLanguageVersion.of(matrix.values.max()))
		val sourceSets = extensions.getByType<SourceSetContainer>()
		val main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
		tasks.named<JavaCompile>(main.compileJavaTaskName) { options.release.set(matrix.values.min()) }

		val rows = matrix.keys.mapNotNull { pinned.release(it) }
		val families = rows.groupBy { it.harness }
		val prepared = rows.associate { release -> release.minecraft to prepare(release, cache, remapper, container) }
		val compiled = rows.associate { release ->
			release.minecraft to compile(release, releaseSources(release, families.getValue(release.harness)), main,
				matrix.getValue(release.minecraft), prepared.getValue(release.minecraft))
		}
		families.forEach { (family, members) ->
			val newest = members.last()
			val sourceSet = sourceSets.create(family)
			releaseSources(newest, members)?.let { sourceSet.java.srcDir(it) }
			sourceSet.compileClasspath = main.output + main.compileClasspath + clientClasspath(prepared.getValue(newest.minecraft))
			tasks.named<JavaCompile>(sourceSet.compileJavaTaskName) { options.release.set(matrix.getValue(newest.minecraft)) }
		}

		tasks.register<PinVanillaClients>("pinVanillaClients") {
			group = GROUP
			description = "Finds every release of ${VanillaClientPinsFile.NAME} in Mojang's version manifest and pins its client."
			this.pins.set(pins)
		}

		tasks.register<GenerateVanillaData>("generateVanillaData") {
			group = GROUP
			description = "Runs the vanilla client of each Paper release of the live matrix, or those " +
				"-P${ReleaseSelection.PROPERTY}= names, and writes the traces of every course and the release's block data."
			val runnable = selected.filterKeys(compiled::containsKey)
			unpinned.set(selected.keys.filterNot { pinned.release(it)?.pinned == true })
			runnable.keys.forEach { minecraft -> dependsOn(compiled.getValue(minecraft), prepared.getValue(minecraft)) }
			runs.set(provider {
				runnable.map { (minecraft, javaVersion) ->
					val release = pinned.release(minecraft)!!
					HarnessRun(
						minecraft,
						javaVersion,
						"$PACKAGE.${release.harness}.${harnessClass(release.harness)}",
						listOf(compiled.getValue(minecraft).get().destinationDirectory.get().asFile) + main.output.files + main.runtimeClasspath.files,
						prepared.getValue(minecraft).get().prepared.get().asFile,
					)
				}
			})
			this.courses.set(courses)
			this.traces.set(traces)
			this.blocks.set(blocks)
			this.cache.set(cache)
			work.set(layout.buildDirectory.dir("vanilla-data"))
			containerBase.set(CONTAINER_BASE)
		}
	}

	private fun Project.prepare(
		release: VanillaClientPins.Release,
		cache: Provider<Directory>,
		remapper: Configuration,
		container: Provider<Boolean>,
	): TaskProvider<PrepareVanillaClient> = tasks.register<PrepareVanillaClient>("prepareVanillaClient-${release.minecraft}") {
		group = GROUP
		description = "Downloads, verifies, remaps and widens the vanilla client of Minecraft ${release.minecraft}."
		this.release.set(release)
		this.cache.set(cache)
		this.remapper.from(remapper)
		accessLists.from(file("src/${release.harness}/${AccessList.FILE_NAME}"),
			file("src/${release.harness}/release-${release.minecraft}/${AccessList.FILE_NAME}"))
		this.container.set(container)
		prepared.set(layout.buildDirectory.dir("vanilla-clients/${release.minecraft}"))
	}

	/**
	 * The directory of [release]'s own sources in its family, `src/<family>/release-<minecraft>/java`, or `null` when
	 * the family's releases share all their sources.
	 *
	 * @throws GradleException when some releases of the family have their own sources and [release] has none
	 */
	private fun Project.releaseSources(release: VanillaClientPins.Release, family: List<VanillaClientPins.Release>): File? {
		val directories = family.associate { it.minecraft to file("src/${it.harness}/release-${it.minecraft}/java") }
		val present = directories.filterValues(File::isDirectory)
		if (present.isEmpty()) return null
		val missing = directories.keys - present.keys
		if (missing.isNotEmpty())
			throw GradleException("The ${release.harness} harness has release sources for ${present.keys}, but not for $missing; " +
				"every release of a family whose members differ needs its own src/${release.harness}/release-<minecraft>/java")
		return present.getValue(release.minecraft)
	}

	private fun Project.compile(
		release: VanillaClientPins.Release,
		releaseSources: File?,
		main: SourceSet,
		javaVersion: Int,
		prepared: TaskProvider<PrepareVanillaClient>,
	): TaskProvider<JavaCompile> {
		val compiler = toolchains.compilerFor(extensions.getByType<JavaPluginExtension>().toolchain)
		return tasks.register<JavaCompile>("compileHarness-${release.minecraft}") {
			description = "Compiles the ${release.harness} harness against the vanilla client of Minecraft ${release.minecraft}."
			source(file("src/${release.harness}/java"))
			releaseSources?.let { source(it) }
			classpath = main.output + main.compileClasspath + clientClasspath(prepared)
			destinationDirectory.set(layout.buildDirectory.dir("classes/harness/${release.minecraft}"))
			options.release.set(javaVersion)
			javaCompiler.set(compiler)
		}
	}

	private fun Project.clientClasspath(prepared: TaskProvider<PrepareVanillaClient>) =
		files(prepared.flatMap { it.prepared }.map { PreparedClient.classpath(it.asFile) })

	/**
	 * The family harness's main class, such as `V1_21_11Harness` of `v1_21_11`.
	 */
	private fun harnessClass(family: String): String = family.replaceFirstChar(Char::uppercase) + "Harness"

	private companion object {
		const val GROUP = "vanilla data"
		const val PAPER = "paper"
		const val PACKAGE = "me.whereareiam.anvil.testkit.physics.harness"
		const val CACHE_PROPERTY = "anvil.vanillaData.cache"
		const val CONTAINER_PROPERTY = "anvil.vanillaData.container"
		const val CONTAINER_BASE = "eclipse-temurin:%d-jre"
	}
}
