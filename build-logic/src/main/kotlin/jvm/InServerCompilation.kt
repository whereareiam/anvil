package me.whereareiam.anvil.buildlogic.jvm

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Compiles [sourceSet] for the in-server Java release, the Java of the oldest supported Minecraft server, and
 * registers `check<SourceSet>ClassRelease`, which `check` runs: none of the source set's classes may be newer than
 * that release, whatever a build file later sets on the compilation. Only such source sets get the check.
 *
 * The `in-server` convention applies it to the main source set. A build file applies it to another source set whose
 * classes servers load, such as the agent operations of an extension that the host also loads:
 *
 * ```kotlin
 * import me.whereareiam.anvil.buildlogic.jvm.compileForInServer
 *
 * compileForInServer(sourceSets.create("agent"))
 * ```
 *
 * @param sourceSet the source set whose classes Minecraft servers load
 */
fun Project.compileForInServer(sourceSet: SourceSet) {
	val javaRelease = the<JavaRelease>()
	if (sourceSet.name == SourceSet.MAIN_SOURCE_SET_NAME) javaRelease.main.set(javaRelease.inServer)
	else tasks.named<JavaCompile>(sourceSet.compileJavaTaskName) { options.release.set(javaRelease.inServer) }

	val name = sourceSet.name
	val classesDirectories = sourceSet.output.classesDirs
	val check = tasks.register<CheckClassRelease>(sourceSet.getTaskName("check", "ClassRelease")) {
		group = LifecycleBasePlugin.VERIFICATION_GROUP
		description = "Verifies that the $name classes run on the in-server Java release."
		classes.from(classesDirectories)
		release.set(javaRelease.inServer)
		report.set(layout.buildDirectory.file("reports/class-release/$name.txt"))
	}
	tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
		dependsOn(check)
	}
}
