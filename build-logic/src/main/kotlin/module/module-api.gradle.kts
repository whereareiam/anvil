import me.whereareiam.toolkit.architecture.model.ArchitectureExtension

plugins {
	id("module-java")
	id("packaging-publication")
}

// Public contracts other families consume; published so consumers can compile against them.
extensions.configure<ArchitectureExtension> {
	kind = api
}
