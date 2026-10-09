package me.whereareiam.anvil.buildlogic.adapter

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile

/**
 * The rule that a segment's classes live in its versioned package, such as `….v1_16_5`, applied as the last action
 * of the segment's `compileJava`, so a misplaced class fails the compilation and cannot reach a JAR or a later task.
 * The compilation declares [packageName] as an input, so its outcome is cached with the rule.
 *
 * @property packageName the last package component every class must have
 */
class SegmentPackageRule(private val packageName: String) : Action<Task> {
	override fun execute(task: Task) {
		val classes = (task as JavaCompile).destinationDirectory.get().asFile
		val misplaced = classes.walkTopDown()
			.filter { it.isFile && it.name.endsWith(".class") && it.name != "module-info.class" }
			.map { it.relativeTo(classes).invariantSeparatorsPath.removeSuffix(".class") }
			.filter { it.substringBeforeLast('/', "").substringAfterLast('/') != packageName }
			.sorted()
			.toList()
		if (misplaced.isNotEmpty())
			throw GradleException("Segment classes must live in a package ending in .$packageName: "
				+ misplaced.joinToString { it.replace('/', '.') })
	}
}
