plugins {
    base
}

description = "Independent test applications loaded and executed by Anvil tests"

val anvilVersion = anvil.versions.anvil.get()

allprojects {
    group = "me.whereareiam.anvil.testkit"
    version = anvilVersion
}

tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}

tasks.named("clean") {
    dependsOn(subprojects.map { "${it.path}:clean" })
}
