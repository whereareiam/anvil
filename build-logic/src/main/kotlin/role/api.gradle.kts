import me.whereareiam.toolkit.architecture.model.ArchitectureExtension

plugins {
	id("jvm")
	id("publication")
}

// Public contracts other families consume; published so consumers can compile against them.
extensions.configure<ArchitectureExtension> {
	kind = api
}
