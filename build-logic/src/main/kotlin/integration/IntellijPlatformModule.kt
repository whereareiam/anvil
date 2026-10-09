package me.whereareiam.anvil.buildlogic.integration

import org.gradle.api.provider.ListProperty

/**
 * IDE features one IntelliJ module builds and tests against, on top of the baseline IntelliJ IDEA platform.
 *
 * The `intellij-platform` convention adds these bundled plugins to the module's platform dependencies, and
 * `intellij-platform-tests` adds them to its `testCurrentIde` run. By default a module uses the Java plugin; a
 * module that also integrates with Gradle adds `org.jetbrains.plugins.gradle`.
 */
interface IntellijPlatformModule {
	/**
	 * Identifiers of the IDE's bundled plugins the module compiles and runs its tests with.
	 */
	val bundledPlugins: ListProperty<String>
}
