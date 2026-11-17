package me.whereareiam.anvil.buildlogic.module

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import java.io.File

/**
 * Verifies that a module's tests use the shared test doubles of the contracts that have one: no test class implements
 * such a contract itself. A fake of its own would have to follow every change of the contract on its own and would
 * drift from the behaviour the shared fixture gives every other test.
 *
 * The check reads the interfaces each compiled test class names directly, so it catches a class declared
 * `implements ProtocolChannel` whatever it is called.
 */
@CacheableTask
abstract class CheckSharedTestDoubles : DefaultTask() {
	/**
	 * The compiled classes of the module's test suites.
	 */
	@get:Classpath
	abstract val classes: ConfigurableFileCollection

	/**
	 * Each contract with a shared test double, by binary name, with the fixture to use instead.
	 */
	@get:Input
	abstract val contracts: MapProperty<String, String>

	/**
	 * Summary of the checked classes.
	 */
	@get:OutputFile
	abstract val report: RegularFileProperty

	@TaskAction
	fun check() {
		val shared = contracts.get()
		val files = classes.files.filter(File::isDirectory)
			.flatMap { root -> root.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.toList() }
		val offending = files.flatMap { file ->
			val reader = ClassReader(file.readBytes())
			implementedContracts(reader.interfaces.asList(), shared)
				.map { contract -> "${reader.className.replace('/', '.')} implements $contract; use ${shared.getValue(contract)}" }
		}.sorted()

		report.get().asFile.writeText("${files.size} test classes checked for own test doubles\n" + offending.joinToString("") { "$it\n" })
		if (offending.isNotEmpty())
			throw GradleException("Tests must use the shared test doubles instead of implementing these contracts themselves:\n"
				+ offending.joinToString("\n") { "  $it" })
	}

	companion object {
		/**
		 * Returns the contracts with a shared test double among a class's directly implemented interfaces.
		 *
		 * @param interfaces the internal names of the interfaces the class names
		 * @param contracts the contracts with a shared test double, by binary name
		 * @return their binary names, in the class's order
		 */
		fun implementedContracts(interfaces: List<String>, contracts: Map<String, String>): List<String> =
			interfaces.map { it.replace('/', '.') }.filter(contracts::containsKey)
	}
}
