plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

architecture {
	kind = assembly
}

description = "Executable CLI and IDE entry points using Anvil's default engine assembly"

toolkitPublish {
	artifactId.set("tooling-launcher")
}

dependencies {
	implementation(projects.anvilLauncher)
	implementation(projects.anvilProtocol.protocolApi)
	implementation(projects.anvilTooling.toolingRunner)
}
