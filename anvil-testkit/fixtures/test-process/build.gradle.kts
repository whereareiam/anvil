plugins {
    id("unit")
}

description = "Small JVM process used to verify lifecycle and shutdown ordering"

tasks.jar {
    manifest.attributes["Main-Class"] = "me.whereareiam.anvil.testkit.fixtures.process.TestProcess"
}
