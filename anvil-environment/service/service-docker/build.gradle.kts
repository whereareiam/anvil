plugins {
	id("module-java")
}

dependencies {
	implementation(libs.docker.java.core)
	implementation(libs.docker.java.transport.zerodep)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilEnvironment.service.serviceApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilEnvironment.service.serviceApi)
}

testing {
	suites {
		// Starts containers on the machine's Docker engine, so test and build leave it out and need no Docker. The
		// anvil.docker.serviceTestImage property names the Redis image they run, which CI copies under that reference.
		register<JvmTestSuite>("dockerTest") {
			useJUnitJupiter(libs.versions.junit)
			dependencies {
				implementation(project())
				implementation(projects.anvilApi)
				implementation(projects.anvilEnvironment.service.serviceApi)
				implementation(libs.docker.java.core)
				implementation(libs.docker.java.transport.zerodep)

				compileOnly(libs.annotations)
			}
			targets.configureEach {
				testTask.configure {
					val image = providers.gradleProperty("anvil.docker.serviceTestImage")
					inputs.property("dockerImage", image)
					jvmArgumentProviders.add(CommandLineArgumentProvider {
						listOf("-Danvil.docker.serviceTestImage=${image.get()}")
					})
				}
			}
		}
	}
}
