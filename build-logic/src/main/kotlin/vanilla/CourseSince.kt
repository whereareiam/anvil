package me.whereareiam.anvil.buildlogic.vanilla

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import org.gradle.api.GradleException
import java.io.File

/**
 * Tells which releases a course of `anvil-testkit/physics/courses` runs on: every release from the Minecraft version
 * its `since` names, or every release when it names none, as the harness runs it. A release before `since` has no
 * trace of the course.
 */
object CourseSince {
	/**
	 * Whether the course in [file] runs on [minecraft].
	 *
	 * @throws GradleException when the course's `since` is no Minecraft version
	 */
	fun runsOn(file: File, minecraft: String): Boolean {
		val since = TomlMapper().readTree(file).path("since")
		if (since.isMissingNode) return true
		if (!since.isTextual || !VERSION.matches(since.asText())) throw GradleException("${file.name}: since must be a Minecraft version")
		return compare(minecraft, since.asText()) >= 0
	}

	/**
	 * Compares two Minecraft versions part by part.
	 */
	fun compare(left: String, right: String): Int {
		val leftParts = left.split('.').map(String::toInt)
		val rightParts = right.split('.').map(String::toInt)
		for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
			val difference = leftParts.getOrElse(index) { 0 }.compareTo(rightParts.getOrElse(index) { 0 })
			if (difference != 0) return difference
		}
		return 0
	}

	private val VERSION = Regex("""\d+(\.\d+)+""")
}
