plugins {
	id("module-java")
}

description = "Text display commands of the server fixture plugin, compiled against the oldest Paper release with them"

// Servers from Minecraft 1.19.4 load this module, and 1.19.4 runs on Java 17.
javaRelease {
	main.set(17)
}

dependencies {
	// The plugin's classes, not its JAR, which embeds this module.
	compileOnly(project(":test-server-plugin")) {
		attributes {
			attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
		}
	}
	compileOnly(libs.bukkit.paper.displays)
}
