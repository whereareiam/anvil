plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Local stand-in for Mojang's profile lookup and session server"

toolkitPublish {
	artifactId.set("service-mojang")
}

dependencies {
	api(projects.anvilApi)

	implementation(libs.jackson.databind)
}
