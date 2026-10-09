plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Test-only Yggdrasil session server and profile lookup for online-mode logins without Mojang"

toolkitPublish {
	artifactId.set("yggdrasil-mock")
}

dependencies {
	api(projects.anvilApi)

	implementation(libs.jackson.databind)
}
