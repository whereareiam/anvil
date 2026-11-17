import me.whereareiam.anvil.buildlogic.module.catalog
import me.whereareiam.anvil.buildlogic.module.library

plugins {
	`java-test-fixtures`
}

// Test doubles a module shares with the tests of the modules that use it, such as a worker kit's test player. They
// serve this repository's tests only, so a published module leaves them out of its publication.
dependencies {
	testFixturesCompileOnly(catalog.library("annotations"))
}

val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations.testFixturesApiElements.get()) { skip() }
javaComponent.withVariantsFromConfiguration(configurations.testFixturesRuntimeElements.get()) { skip() }
