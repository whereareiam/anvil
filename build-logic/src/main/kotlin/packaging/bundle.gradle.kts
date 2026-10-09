import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import me.whereareiam.anvil.buildlogic.jvm.JavaRelease

plugins {
	id("jvm")
	id("com.gradleup.shadow")
}

val javaRelease = the<JavaRelease>()

// Modules copied into the shaded JAR. They resolve like a runtime classpath for the main release, so a
// bundle cannot embed classes compiled for a newer Java than its own main classes.
val embedded = configurations.dependencyScope("embedded")
val embeddedClasspath = configurations.resolvable("embeddedClasspath") {
	extendsFrom(embedded.get())
	attributes {
		attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
		attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
		attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
		attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
		attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaRelease.main)
	}
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
	archiveClassifier.set("")
	configurations.set(embeddedClasspath.map { setOf<Configuration>(it) })
	filesMatching("META-INF/services/**") {
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
	}
	mergeServiceFiles()
}

// The shaded JAR is the runtime artifact; the plain JAR keeps a distinct file name.
tasks.named<Jar>("jar") {
	archiveClassifier.set("plain")
}

configurations.named("runtimeElements") {
	outgoing.artifacts.clear()
	outgoing.artifact(shadowJar)
}

tasks.named("assemble") {
	dependsOn(shadowJar)
}
