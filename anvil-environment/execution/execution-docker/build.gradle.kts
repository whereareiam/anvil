plugins {
	id("module-java")
	id("test-fixtures-consumer")
}

dependencies {
	implementation(libs.docker.java.core)
	implementation(libs.docker.java.transport.zerodep)
	implementation(libs.jackson.databind)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilEnvironment.execution.executionApi)

	testImplementation(projects.anvilEnvironment.execution.executionApi)
	testImplementation(projects.anvilEnvironment.execution.executionManaged)
}

fixtures {
	process()
}

testing {
	suites {
		// Starts containers on the machine's Docker engine, so test and build leave it out and need no Docker. The
		// anvil.docker.testImage property names the image they run in and anvil.docker.containerTestImage the Redis they run
		// as a container process, which CI caches under the same references.
		register<JvmTestSuite>("dockerTest") {
			useJUnitJupiter(libs.versions.junit)
			dependencies {
				implementation(project())
				implementation(projects.anvilApi)
				implementation(projects.anvilEnvironment.execution.executionApi)
				implementation(projects.anvilEnvironment.execution.executionManaged)
				implementation(projects.anvilTestkit.support)

				compileOnly(libs.annotations)
			}
			targets.configureEach {
				testTask.configure {
					val image = providers.gradleProperty("anvil.docker.testImage")
					val containerImage = providers.gradleProperty("anvil.docker.containerTestImage")
					inputs.property("dockerImage", image)
					inputs.property("containerImage", containerImage)
					jvmArgumentProviders.add(CommandLineArgumentProvider {
						listOf("-Danvil.docker.testImage=${image.get()}", "-Danvil.docker.containerTestImage=${containerImage.get()}")
					})
				}
			}
		}
	}
}
