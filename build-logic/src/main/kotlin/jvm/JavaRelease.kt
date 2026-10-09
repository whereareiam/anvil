package me.whereareiam.anvil.buildlogic.jvm

import org.gradle.api.provider.Property

/**
 * The Java releases a project compiles for.
 *
 * The `jvm-base` convention defaults [main] to the catalog's `java` version and [inServer] to `java-in-server`;
 * the `in-server` trait compiles the main classes for [inServer]. Other source sets, such as tests, keep the
 * build's release unless a build file compiles them for [inServer] through `compileForInServer`, which also checks
 * them with `check<SourceSet>ClassRelease`, so none of their classes is newer. Gradle publishes the main release
 * as the variants' target JVM version, so a project can depend only on projects whose main release is not newer.
 */
interface JavaRelease {
	/**
	 * Release passed to `compileJava`.
	 */
	val main: Property<Int>

	/**
	 * Release of classes that Minecraft servers load: the Java of the oldest supported server.
	 */
	val inServer: Property<Int>
}
