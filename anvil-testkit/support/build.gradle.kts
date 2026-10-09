plugins {
	id("module-java")
	id("test-fixtures-consumer")
}

description = "Host-side access and classloader lifetime for prepared test artifacts"

dependencies {
	compileOnlyApi(libs.annotations)
}

fixtures {
	process()
	extension()
	brokenExtension()
	observationExtension()
}
