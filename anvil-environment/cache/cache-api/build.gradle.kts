plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

architecture {
	kind = api
}

description = "Independent cache entry and staged publication contracts"

toolkitPublish {
	artifactId.set("cache-api")
}

dependencies {
	api(libs.annotations)
}
