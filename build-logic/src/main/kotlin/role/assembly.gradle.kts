import me.whereareiam.toolkit.architecture.model.ArchitectureExtension

plugins {
	id("jvm")
	id("publication")
}

// Composes implementations of several families into one shipped artifact.
extensions.configure<ArchitectureExtension> {
	kind = assembly
}
