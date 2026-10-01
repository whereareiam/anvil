plugins {
	alias(libs.plugins.toolkit.publish.maven)
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

architecture {
	kind = api
}

description = "Tracked Gradle artifact inputs shared by Anvil JVM tasks and project export"

dependencies {
	api(libs.annotations)

	compileOnly(gradleApi())
}

java.withJavadocJar()

toolkitPublish {
	artifactId.set("gradle-artifacts")
}
