package me.whereareiam.anvil.buildlogic.library

import org.gradle.api.provider.ListProperty

/**
 * The Maven repositories that protocol library releases are downloaded from, declared by the settings as data:
 *
 * ```
 * libraryRepositories {
 *     url("https://repo1.maven.org/maven2/")
 *     url("https://jitpack.io/")
 * }
 * ```
 *
 * `pinLibraryReleases` records, for each JAR of a release closure, the first of these repositories that serves the
 * bytes Gradle resolved, so a pinned URL always names one of them. Gradle itself resolves from the repositories of
 * the dependency resolution management, which must include every library repository.
 */
abstract class LibraryRepositories {
	/**
	 * Root URLs of the repositories, in the order they are searched.
	 */
	abstract val urls: ListProperty<String>

	/**
	 * Adds the repository rooted at [url].
	 */
	fun url(url: String) {
		urls.add(url)
	}
}
