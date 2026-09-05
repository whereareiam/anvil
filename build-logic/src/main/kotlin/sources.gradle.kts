plugins.withId("java") {
	extensions.configure<JavaPluginExtension> {
		withSourcesJar()
	}
}
