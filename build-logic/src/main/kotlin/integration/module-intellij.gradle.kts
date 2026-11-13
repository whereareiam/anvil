import me.whereareiam.anvil.buildlogic.integration.IntellijPlatformModule
import me.whereareiam.anvil.buildlogic.module.catalog
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
	id("module-java")
	id("org.jetbrains.intellij.platform")
}

// Modules compile against the oldest supported IntelliJ IDEA. Modules with tests apply test-intellij,
// which runs them in the platform.
val module = extensions.create<IntellijPlatformModule>("intellijPlatformModule")
module.bundledPlugins.convention(listOf("com.intellij.java"))

val baselineIde = catalog.findVersion("intellij-idea")
	.orElseThrow { GradleException("The libs catalog has no 'intellij-idea' version") }
	.requiredVersion

dependencies {
	intellijPlatform {
		create("IC", baselineIde)
		bundledPlugins(module.bundledPlugins)
	}
}

// Kotlin views run on the Kotlin runtime the baseline IDE bundles, so they must not call a newer standard library.
plugins.withId("org.jetbrains.kotlin.jvm") {
	extensions.configure<KotlinJvmProjectExtension> {
		compilerOptions {
			apiVersion.set(KotlinVersion.KOTLIN_2_1)
			languageVersion.set(KotlinVersion.KOTLIN_2_2)
		}
	}
}

// java-test-fixtures makes the fixtures depend on their own module, and the platform plugin requests that project
// dependency as the composed jar. The tests reach it through the fixtures, so their compile classpath asks for the
// composed jar too, or it selects the module a second time with incompatible library elements.
plugins.withId("java-test-fixtures") {
	configurations.testCompileClasspath {
		attributes {
			attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(Attributes.COMPOSED_JAR_NAME))
		}
	}
}

// Native instrumentation tasks share the project's Ant builder.
tasks.named("instrumentTestCode") {
	mustRunAfter(tasks.named("instrumentCode"))
}
