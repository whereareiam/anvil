package me.whereareiam.anvil.buildlogic.vanilla.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.GradleException
import java.io.File

/**
 * Mojang's version metadata of one release, `<minecraft>.json`: the client's downloads and the libraries it runs
 * with, each by path, URL and SHA-1, with the rules and natives that choose them per [ClientPlatform].
 *
 * Libraries choose platforms as Mojang's launcher does. A library without rules runs everywhere; otherwise its rules
 * apply in order and the last one whose `os` matches decides. Natives come in two layouts: until 1.18.2 a library
 * names its natives classifier per system in `natives`, downloaded from `downloads.classifiers` and built for
 * `x86_64` only; from 1.19 each natives JAR is a library of its own whose classifier names its system and
 * architecture, such as `natives-macos-arm64`.
 *
 * @property minecraft the release's version, its `id`
 * @property client the client JAR
 * @property mappings the client's mappings, `null` for a client Mojang ships unobfuscated
 * @property libraries the libraries in the metadata's order
 */
class VersionMetadata(
	val minecraft: String,
	val client: Download,
	val mappings: Download?,
	val libraries: List<Library>,
) {
	/**
	 * A file of the release by its path below the libraries directory, its URL and SHA-1.
	 *
	 * @property path the Maven-layout path, such as `org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar`; the file name for the
	 * client and its mappings
	 */
	data class Download(val path: String, val url: String, val sha1: String)

	/**
	 * One library entry.
	 *
	 * @property name the Maven coordinates, a fourth part naming its classifier
	 * @property artifact its JAR, `null` for an entry that only carries natives
	 * @property rules its rules, empty when it runs everywhere
	 * @property natives its natives JAR per system, the layout until 1.18.2
	 */
	data class Library(
		val name: String,
		val artifact: Download?,
		val rules: List<Rule>,
		val natives: Map<String, Download>,
	) {
		/**
		 * The architecture the library's own JAR is natives for, or `null` when it is no natives JAR.
		 */
		val nativeArch: String?
			get() {
				val classifier = name.split(':').getOrNull(3) ?: return null
				if (!classifier.startsWith("natives-")) return null
				return when {
					classifier.endsWith("-arm64") -> "arm64"
					classifier.endsWith("-x86") -> "x86"
					else -> "x86_64"
				}
			}

		/**
		 * Whether the library's rules let it run on [platform].
		 */
		fun allowed(platform: ClientPlatform): Boolean {
			if (rules.isEmpty()) return true
			var allowed = false
			for (rule in rules)
				if (rule.matches(platform)) allowed = rule.allow
			return allowed
		}
	}

	/**
	 * A library rule: allow or disallow, on every system or on one.
	 *
	 * @property allow whether the rule allows the library
	 * @property os the system it applies to, `null` for every system
	 * @property arch the architecture it applies to, `null` for every architecture; Mojang's `x86` means 32-bit
	 */
	data class Rule(val allow: Boolean, val os: String?, val arch: String?) {
		/**
		 * Whether the rule applies on [platform].
		 */
		fun matches(platform: ClientPlatform): Boolean =
			(os == null || os == platform.os) && (arch == null || arch == platform.arch)
	}

	/**
	 * The natives JARs the client loads on [platform]: those built for its system and architecture.
	 */
	fun natives(platform: ClientPlatform): List<Download> = libraries.filter { it.allowed(platform) }.flatMap { library ->
		val own = library.artifact?.takeIf { library.nativeArch == platform.arch }
		val legacy = library.natives[platform.os]?.takeIf { platform.arch == "x86_64" }
		listOfNotNull(own, legacy)
	}

	/**
	 * Whether Mojang ships natives of the client's libraries for [platform], so the client runs there.
	 */
	fun runsOn(platform: ClientPlatform): Boolean = natives(platform).isNotEmpty()

	/**
	 * The libraries the client runs with on [platform], natives included, in the metadata's order and each once;
	 * the client JAR itself is not among them.
	 */
	fun libraries(platform: ClientPlatform): List<Download> {
		val natives = natives(platform)
		val plain = libraries.filter { it.allowed(platform) && it.nativeArch == null }.mapNotNull { it.artifact }
		return (plain + natives).distinctBy { it.path }
	}

	companion object {
		/**
		 * Reads the metadata in [file].
		 *
		 * @throws GradleException when the file is no version metadata
		 */
		fun read(file: File): VersionMetadata = parse(ObjectMapper().readTree(file), file.name)

		/**
		 * Reads the metadata [document], named [source] in failures.
		 *
		 * @throws GradleException when the document is no version metadata
		 */
		fun parse(document: JsonNode, source: String): VersionMetadata {
			val id = document.path("id").asText("")
			if (id.isEmpty()) throw GradleException("$source names no Minecraft version id")
			val downloads = document.path("downloads")
			val client = download(downloads.path("client"), "client.jar", "$source client")
				?: throw GradleException("$source names no client download")
			val mappings = download(downloads.path("client_mappings"), "client.txt", "$source client mappings")
			val libraries = document.path("libraries").map { library(it, source) }
			return VersionMetadata(id, client, mappings, libraries)
		}

		private fun library(node: JsonNode, source: String): Library {
			val name = node.path("name").asText("")
			if (name.isEmpty()) throw GradleException("$source lists a library without a name")
			val downloads = node.path("downloads")
			val artifact = download(downloads.path("artifact"), null, "$source $name")
			val rules = node.path("rules").map { rule ->
				val os = rule.path("os")
				Rule(rule.path("action").asText() == "allow", os.path("name").textValue(), os.path("arch").textValue())
			}
			val natives = node.path("natives").properties().associate { (os, classifier) ->
				val nativesClassifier = classifier.asText().replace("\${arch}", "64")
				os to (download(downloads.path("classifiers").path(nativesClassifier), null, "$source $name $nativesClassifier")
					?: throw GradleException("$source $name names natives $nativesClassifier for $os without a download"))
			}
			return Library(name, artifact, rules, natives)
		}

		private fun download(node: JsonNode, path: String?, context: String): Download? {
			if (node.isMissingNode || node.isNull) return null
			val url = node.path("url").asText("")
			val sha1 = node.path("sha1").asText("")
			if (url.isEmpty() || sha1.isEmpty()) throw GradleException("$context has no url or sha1")
			return Download(path ?: node.path("path").asText().ifEmpty { throw GradleException("$context has no path") }, url, sha1)
		}
	}
}
