plugins {
    base
}

description = "Reusable artifacts installed by runtime and server tests"

tasks.named("build") {
    dependsOn(":anvil-testing:testing-fixtures:fixtures-server-plugin:build",
        ":anvil-testing:testing-fixtures:fixtures-external-extension:build")
}
