plugins {
	id("module-api")
}

description = "Editor-independent discovery and live session contracts for Anvil"

dependencies {
	api(libs.annotations)
	api(libs.jackson.databind)
}
