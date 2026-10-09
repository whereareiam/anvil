package me.whereareiam.anvil.buildlogic.platform

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Verifies that the agent a platform provider installs runs on exactly the oldest Java its version data lets
 * planning select: the agent targets the release `[agent] minimumJava` names, and no class the agent loads,
 * including embedded code, needs newer Java.
 *
 * A provider that declares an agent minimum must name the agent it installs, and a provider that names an agent
 * must declare its minimum, so neither side of the rule can be left unchecked. Requiring the declared minimum to
 * equal the agent's release keeps two providers that install the same agent, such as Paper and Spigot with the
 * Bukkit agent, from declaring different minimums.
 */
@CacheableTask
abstract class AgentJavaCheck : DefaultTask() {
	/**
	 * The provider's `<platform>-versions.toml`; exactly one file.
	 */
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val versionData: ConfigurableFileCollection

	/**
	 * Runtime JARs of the agent the provider installs.
	 */
	@get:Classpath
	abstract val agents: ConfigurableFileCollection

	/**
	 * Java releases the agent's runtime variants target (their `org.gradle.jvm.version` attribute); a variant
	 * without the attribute adds nothing.
	 */
	@get:Input
	abstract val agentReleases: ListProperty<Int>

	/**
	 * Summary of the declared minimum and the checked JARs.
	 */
	@get:OutputFile
	abstract val report: RegularFileProperty

	@TaskAction
	fun check() {
		val data = versionData.files.singleOrNull()
			?: throw GradleException("A platform provider ships exactly one *-versions.toml resource; found "
				+ versionData.files.map(File::getName).sorted())
		val minimum = PlatformVersionsFile.agentMinimumJava(data)
		val jars = agents.files.sortedBy(File::getName)
		if (minimum == null && jars.isNotEmpty())
			throw GradleException("${data.name} declares no [agent] minimumJava, but the provider installs "
				+ jars.joinToString { it.name })
		if (minimum != null && jars.isEmpty())
			throw GradleException("${data.name} declares [agent] minimumJava = $minimum, but the provider names no "
				+ "platformAgent dependency to check it against")

		val releases = agentReleases.get().distinct().sorted()
		val failures = if (minimum == null) emptyList() else jars.flatMap { AgentJarClasses.newerThan(it, minimum) }
		report.get().asFile.writeText(buildString {
			appendLine("agent minimum Java: ${minimum ?: "none"}")
			appendLine("agent release: ${releases.joinToString().ifEmpty { "none" }}")
			appendLine("checked: ${jars.joinToString { it.name }.ifEmpty { "none" }}")
			failures.forEach(::appendLine)
		})
		if (failures.isNotEmpty())
			throw GradleException("The platform agent needs newer Java than ${data.name} declares ([agent] minimumJava = "
				+ "$minimum); compile the agent for Java $minimum or raise the declaration:\n"
				+ failures.take(reportedFailures).joinToString("\n") { "  $it" }
				+ if (failures.size > reportedFailures) "\n  ... and ${failures.size - reportedFailures} more" else "")

		val other = releases.filter { it != minimum }
		if (minimum == null || other.isEmpty()) return

		throw GradleException("${data.name} declares [agent] minimumJava = $minimum, but the agent targets Java "
			+ other.joinToString() + "; declare the agent's release, so planning neither selects Java the agent "
			+ "cannot run on nor refuses Java it can")
	}

	private companion object {
		const val reportedFailures = 20
	}
}
