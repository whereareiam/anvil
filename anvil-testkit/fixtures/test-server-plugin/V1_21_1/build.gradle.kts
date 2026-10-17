plugins {
	id("module-java")
}

description = "Cookie and text display commands of the server fixture plugin, compiled against the Paper release that has both"

dependencies {
	// The plugin's classes, not its JAR, which embeds this module.
	compileOnly(project(":test-server-plugin")) {
		attributes {
			attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
		}
	}
	compileOnly(libs.bukkit.paper.cookies)
}
