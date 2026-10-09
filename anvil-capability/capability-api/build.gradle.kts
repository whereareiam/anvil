plugins {
	id("api")
}

description = "Shared capability composition, player lifecycle, and typed request contracts"

dependencies {
	api(projects.anvilApi)
}
