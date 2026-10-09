import me.whereareiam.toolkit.architecture.model.ArchitectureExtension

plugins {
	id("module-java")
	id("packaging-publication")
}

// Composes implementations of several families into one shipped artifact.
extensions.configure<ArchitectureExtension> {
	kind = assembly
}
