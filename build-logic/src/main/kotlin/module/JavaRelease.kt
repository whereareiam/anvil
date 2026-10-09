package me.whereareiam.anvil.buildlogic.module

import org.gradle.api.provider.Property

/**
 * The Java releases a project compiles for.
 *
 * The `module-defaults` convention defaults [main] to the catalog's `java` version and [legacy] to `java-legacy`;
 * the `module-java-legacy` trait compiles the main classes for [legacy]. Other source sets, such as tests, keep the
 * build's release unless a build file compiles them for [legacy] through `compileForLegacyJava`, which also checks
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
	val legacy: Property<Int>
}
