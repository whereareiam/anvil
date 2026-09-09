plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Shared capability composition, player lifecycle, and typed request contracts"

dependencies {
    api(projects.anvilApi)
}
