plugins {
	id("module-api")
}

description = "Adventure text shared by the MCProtocolLib client segments of releases whose components are Adventure's"

dependencies {
	compileOnly(libs.adventure.api)

	testImplementation(libs.adventure.api)
}
