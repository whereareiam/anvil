plugins {
	`java-gradle-plugin`
	id("jvm-base")
	id("publication")
	id("descriptor-version")
}

// Gradle plugins are Kotlin-only, so they take the JVM defaults without Lombok. Modules apply `kotlin-dsl`
// themselves: its plugin artifact pins the Kotlin standard library strictly to Gradle's embedded version, which
// conflicts with the other plugin dependencies of this build logic. The Anvil base plugin reads its version from
// the manifest that descriptor-version writes, so the Anvil modules it adds match the applied plugin.

// java-gradle-plugin publishes the plugin through its own pluginMaven publication and markers.
toolkitPublish {
	name.set("pluginMaven")
	includeComponent.set(false)
}

java {
	withSourcesJar()
	withJavadocJar()
}

dependencies {
	testImplementation(gradleTestKit())
}
