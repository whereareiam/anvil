plugins {
	`kotlin-dsl`
	alias(libs.plugins.toolkit.architecture)
	id("gradle-plugin")
}

description = "Gradle adapters for Anvil platform providers"

dependencies {
	implementation(projects.anvilIntegration.integrationGradle.gradleBase)
	testImplementation(gradleTestKit())
}

gradlePlugin {
	plugins {
		create("anvilPlatformPaper") {
			id = "me.whereareiam.anvil.platform.paper"
			implementationClass = "me.whereareiam.anvil.integration.gradle.platform.PaperPlugin"
			displayName = "Anvil Paper Platform"
			description = "Includes the Paper platform provider and agent"
		}
		create("anvilPlatformSpigot") {
			id = "me.whereareiam.anvil.platform.spigot"
			implementationClass = "me.whereareiam.anvil.integration.gradle.platform.SpigotPlugin"
			displayName = "Anvil Spigot Platform"
			description = "Includes the Spigot platform provider and agent"
		}
		create("anvilPlatformVelocity") {
			id = "me.whereareiam.anvil.platform.velocity"
			implementationClass = "me.whereareiam.anvil.integration.gradle.platform.VelocityPlugin"
			displayName = "Anvil Velocity Platform"
			description = "Includes the Velocity platform provider and agent"
		}
		create("anvilPlatformBungeeCord") {
			id = "me.whereareiam.anvil.platform.bungeecord"
			implementationClass = "me.whereareiam.anvil.integration.gradle.platform.BungeeCordPlugin"
			displayName = "Anvil BungeeCord Platform"
			description = "Includes the BungeeCord platform provider and agent"
		}
	}
}

toolkitPublish {
	name.set("pluginMaven")
	artifactId.set("gradle-platforms")
}
