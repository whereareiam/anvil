plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

architecture {
    kind = assembly
}

description = "Reusable foreground scenario runner for Anvil tooling"

toolkitPublish {
    artifactId.set("tooling-runner")
}

dependencies {
	api(projects.anvilApi)

	implementation(projects.anvilLauncher)
}
