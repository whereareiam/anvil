val repository = rootProject.layout.buildDirectory.dir("gradle-fixtures/repository")
val preparation = rootProject.tasks.named("prepareGradleFixtureRepository")

tasks.withType<Test>().configureEach {
	dependsOn(preparation)
	inputs.dir(repository).withPropertyName("anvilFixtureRepository").withPathSensitivity(PathSensitivity.RELATIVE)
	systemProperty("anvil.test.repository", repository.get().asFile.absolutePath)
	systemProperty("anvil.test.version", project.version.toString())
}
