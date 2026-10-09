import me.whereareiam.anvil.buildlogic.integration.IntellijPlatformModule
import me.whereareiam.anvil.buildlogic.jvm.catalog

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

// Native instrumentation tasks share the project's Ant builder.
tasks.named("instrumentTestCode") {
	mustRunAfter(tasks.named("instrumentCode"))
}
