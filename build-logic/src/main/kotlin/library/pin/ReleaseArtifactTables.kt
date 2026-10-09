package me.whereareiam.anvil.buildlogic.library.pin

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import me.whereareiam.anvil.buildlogic.library.ReleaseArtifact
import org.gradle.api.GradleException

/**
 * Rewrites the `[[release.artifact]]` tables of a releases file's text while keeping everything people write:
 * the header comments, each `[[release]]` table with its fields, blank lines and comments, inline comments included,
 * and the comments placed between a release's last artifact and the next `[[release]]` with their layout.
 * Artifact tables are generated lock data, so a comment inside them, whether on its own line or after a value or
 * table header, has no stable place and is refused rather than dropped.
 */
object ReleaseArtifactTables {
	private val releaseHeader = Regex("""\s*\[\[\s*release\s*]]\s*(#.*)?""")
	private val artifactHeader = Regex("""\s*\[\[\s*release\.artifact\s*]]\s*(#.*)?""")
	private val tableHeader = Regex("""\s*\[.*""")
	private val toml = TomlMapper()

	/**
	 * Replaces the artifact tables of every release in [text] with [closures], keyed by release version, in their
	 * given order. Tables are separated by one blank line.
	 *
	 * @throws GradleException when the text has a table this file format does not define, a comment inside the
	 * artifact tables, or a release without a closure
	 */
	fun replace(text: String, closures: Map<String, List<ReleaseArtifact>>): String {
		val lines = text.lines().let { if (text.endsWith("\n")) it.dropLast(1) else it }
		lines.firstOrNull { tableHeader.matches(it) && !releaseHeader.matches(it) && !artifactHeader.matches(it) }?.let {
			throw GradleException("Releases file has an unexpected table '${it.trim()}'")
		}

		val starts = lines.indices.filter { releaseHeader.matches(lines[it]) }
		val output = lines.subList(0, starts.firstOrNull() ?: lines.size).toMutableList()
		starts.forEachIndexed { index, start ->
			val last = index == starts.lastIndex
			val block = lines.subList(start, starts.getOrElse(index + 1) { lines.size })
			val trailing = release(block, closures, output).dropWhile(String::isBlank)
			val kept = if (last) trailing.dropLastWhile(String::isBlank) else trailing
			if (kept.isEmpty()) {
				if (!last) output += ""
				return@forEachIndexed
			}
			output += ""
			output += kept
		}
		return output.joinToString("\n") + "\n"
	}

	/**
	 * Appends one release with its new artifact tables to [output].
	 *
	 * @return the lines after the release's last artifact value: comments that lead into the next release, or
	 * that end the file, with their blank lines
	 */
	private fun release(block: List<String>, closures: Map<String, List<ReleaseArtifact>>, output: MutableList<String>): List<String> {
		val firstArtifact = block.indexOfFirst { artifactHeader.matches(it) }.takeIf { it >= 0 } ?: block.size
		val table = block.subList(0, firstArtifact).dropLastWhile(String::isBlank)
		val version = toml.readTree(table.joinToString("\n")).path("release").path(0).path("version").asText()
		val closure = closures[version] ?: throw GradleException("Releases file has no resolved closure for release '$version'")

		val artifactLines = block.subList(firstArtifact, block.size)
		val lastData = artifactLines.indexOfLast { it.isNotBlank() && !isComment(it) }
		artifactLines.subList(0, lastData + 1).firstOrNull(::hasComment)?.let {
			throw GradleException("Release $version has a comment inside its [[release.artifact]] tables, which pinning "
				+ "regenerates; move '${it.trim()}' into the [[release]] table")
		}

		output += table
		closure.forEach { artifact ->
			output += ""
			output += "[[release.artifact]]"
			output += "module = ${quote(artifact.module)}"
			output += "url = ${quote(artifact.url)}"
			output += "sha256 = ${quote(artifact.sha256)}"
		}
		return artifactLines.subList(lastData + 1, artifactLines.size)
	}

	private fun isComment(line: String): Boolean = line.trimStart().startsWith("#")

	/**
	 * Whether [line] holds a comment: a `#` outside basic (`"…"`) and literal (`'…'`) strings.
	 */
	private fun hasComment(line: String): Boolean {
		var quote: Char? = null
		var escaped = false
		for (character in line) {
			if (escaped) {
				escaped = false
				continue
			}
			if (quote == '"' && character == '\\') {
				escaped = true
				continue
			}
			if (quote != null) {
				if (character == quote) quote = null
				continue
			}
			if (character == '"' || character == '\'') quote = character
			if (character == '#') return true
		}
		return false
	}

	private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
