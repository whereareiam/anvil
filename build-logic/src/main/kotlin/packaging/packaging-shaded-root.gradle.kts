import io.freefair.gradle.plugins.lombok.tasks.Delombok

plugins {
	java
	id("packaging-shaded-jar")
}

// A wiring root ships the code of the projects it embeds, besides any code of its own, such as the agent-backed
// provider of a process capability family. Consumers compile against its shaded JAR, which carries the embedded
// public types, and its sources and Javadoc JARs describe the embedded projects too. Each embedded project offers its
// source directories as the Java plugin's main-sources variant, so the root reads them without reaching into the
// project.
configurations.named("apiElements") {
	outgoing.artifacts.clear()
	outgoing.artifact(tasks.named("shadowJar"))
	// Projects of the same build would compile against the root's own, empty class directories otherwise.
	outgoing.variants.clear()
}

val embeddedSources = configurations.named("embeddedClasspath").map { classpath ->
	classpath.incoming.artifactView {
		withVariantReselection()
		componentFilter { it is ProjectComponentIdentifier }
		attributes {
			attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.VERIFICATION))
			attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.MAIN_SOURCES))
		}
	}.files
}

tasks.withType<Jar>().matching { it.name == "sourcesJar" }.configureEach {
	from(embeddedSources)
}

// Javadoc reads the embedded sources after Lombok's code generation, like the root's own sources would be read.
tasks.named<Delombok>("delombok") {
	input.from(embeddedSources)
}

tasks.named<Javadoc>("javadoc") {
	include("**/*.java")
}
