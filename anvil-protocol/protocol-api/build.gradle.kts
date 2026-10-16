plugins {
	id("module-api")
}

description = "Library-neutral simulated-player protocol contracts for Anvil"

dependencies {
	api(projects.anvilApi)

	// Lombok's Jackson builders of the worker messages only annotate them; the worker reads them with its own Jackson.
	compileOnly(libs.jackson.databind)
}
