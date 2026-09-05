plugins {
    base
}

tasks.named("build") {
    dependsOn(":anvil-integration:junit:build")
}
