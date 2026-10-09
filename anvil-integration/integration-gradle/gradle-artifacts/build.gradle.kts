plugins {
	id("module-api")
}

description = "Tracked Gradle artifact inputs shared by Anvil JVM tasks and project export"

dependencies {
	api(libs.annotations)

	compileOnly(gradleApi())
}
