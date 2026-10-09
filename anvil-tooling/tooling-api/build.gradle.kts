plugins {
	id("api")
}

description = "Editor-independent discovery and live session contracts for Anvil"

dependencies {
	api(libs.annotations)
	api(libs.jackson.databind)
}
