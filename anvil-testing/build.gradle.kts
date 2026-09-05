plugins {
    base
}

description = "Cross-module runtime checks, live server tests, and their fixture artifacts"

tasks.named("build") {
    dependsOn(":anvil-testing:testing-runtime:build", ":anvil-testing:testing-server:build",
        ":anvil-testing:testing-fixtures:build")
}
