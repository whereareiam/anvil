plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

description = "Shared agent transport and runtime implementation"

dependencies {
    compileOnly(projects.anvilAgent.agentApi)

    testImplementation(projects.anvilAgent.agentApi)

    testCompileOnly(libs.annotations)
}
