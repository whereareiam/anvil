package me.whereareiam.anvil.buildlogic.fixtures

import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.testing.Test

/** Declares fixture artifacts consumed as tracked files, outside the parent test classpath. */
open class FixtureInputs(private val project: Project) {
	private val registered = mutableSetOf<String>()

	fun process() = register("process", "test-process")
	fun serverPlugin() = register("server-plugin", "test-server-plugin")
	fun extension() = register("extension-normal", "test-extension", "normal")
	fun brokenExtension() = register("extension-broken", "test-extension", "broken")
	fun observationExtension() = register("extension-observation", "test-extension", "observation")

	private fun register(key: String, artifact: String, variant: String? = null) {
		if (!registered.add(key)) return

		val configurationName = "fixture" + key.split('-').joinToString("") { part -> part.replaceFirstChar(Char::uppercase) }
		val configuration = project.configurations.create(configurationName) {
			isCanBeConsumed = false
			isCanBeResolved = true
			isTransitive = false
			attributes {
				attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
				attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.LIBRARY))
				attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements::class.java, LibraryElements.JAR))
				if (variant != null) attribute(Attribute.of("me.whereareiam.anvil.testkit.variant", String::class.java), variant)
			}
		}
		project.dependencies.add(configuration.name, "me.whereareiam.anvil.testkit:$artifact:${project.version}")
		val arguments = project.objects.newInstance(FixtureJvmArguments::class.java).apply {
			propertyName.set("anvil.testkit.fixture.$key")
			artifactFiles.from(configuration)
		}
		project.tasks.withType(Test::class.java).configureEach {
			jvmArgumentProviders.add(arguments)
		}
	}
}
