plugins {
    id("unit")
}

description = "Server plugin and native agent operations used by live Anvil tests"

dependencies {
    compileOnly(anvil.agent.server.api)
    compileOnly(anvil.api)
    compileOnly(libs.paper)
}

tasks.processResources {
    inputs.property("version", project.version.toString())
    expand("version" to project.version.toString())
}
