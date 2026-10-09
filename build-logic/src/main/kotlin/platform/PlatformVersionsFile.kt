package me.whereareiam.anvil.buildlogic.platform

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import org.gradle.api.GradleException
import java.io.File

/**
 * Reads the build-relevant part of a platform provider's `<platform>-versions.toml`: the oldest Java the agent the
 * provider installs runs on (`[agent] minimumJava`). Platform planning reads and validates the whole file at runtime.
 */
object PlatformVersionsFile {
	/**
	 * Returns the agent's minimum Java declared by [file].
	 *
	 * @return declared Java feature version, or null when the file declares no `[agent]` table
	 * @throws GradleException when the file is not TOML or the declaration is not an integer
	 */
	fun agentMinimumJava(file: File): Int? {
		val document = try {
			TomlMapper().readTree(file)
		} catch (exception: JacksonException) {
			throw GradleException("${file.name} is not valid TOML: ${exception.originalMessage}", exception)
		}

		val agent = document.path("agent")
		if (agent.isMissingNode) return null
		val minimumJava = agent.path("minimumJava")
		if (!minimumJava.isIntegralNumber)
			throw GradleException("${file.name} must declare [agent] minimumJava as a Java feature version")
		return minimumJava.intValue()
	}
}
