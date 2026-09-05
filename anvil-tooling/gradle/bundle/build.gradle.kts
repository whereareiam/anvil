plugins {
	`kotlin-dsl`
	alias(libs.plugins.toolkit.architecture)
	id("gradle-plugin")
}

description = "Combined Anvil Gradle plugin"

dependencies {
	implementation(projects.anvilIntegration.junit.gradle)
	implementation(projects.anvilTooling.gradle.scenarios)

	testImplementation(projects.anvilIntegration.junit.extension)
	testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("anvil") {
            id = "me.whereareiam.anvil"
            implementationClass = "me.whereareiam.anvil.gradle.AnvilPlugin"
            displayName = "Anvil"
            description = "Installs automated Anvil tests and foreground scenarios"
        }
        create("anvilCapabilityDefault") {
            id = "me.whereareiam.anvil.capability.default"
            implementationClass = "me.whereareiam.anvil.gradle.unit.DefaultPlugin"
            displayName = "Anvil Default Capabilities"
            description = "Includes Anvil's default capability wiring"
        }
        create("anvilCapabilitySession") {
            id = "me.whereareiam.anvil.capability.session"
            implementationClass = "me.whereareiam.anvil.gradle.unit.SessionPlugin"
            displayName = "Anvil Session Capability"
            description = "Includes the built-in Session capability"
        }
        create("anvilCapabilityServer") {
            id = "me.whereareiam.anvil.capability.server"
            implementationClass = "me.whereareiam.anvil.gradle.unit.ServerPlugin"
            displayName = "Anvil Server Capability"
            description = "Includes the built-in Server capability"
        }
        create("anvilCapabilityMessages") {
            id = "me.whereareiam.anvil.capability.messages"
            implementationClass = "me.whereareiam.anvil.gradle.unit.MessagesPlugin"
            displayName = "Anvil Messages Capability"
            description = "Includes the built-in Messages capability"
        }
        create("anvilCapabilityMovement") {
            id = "me.whereareiam.anvil.capability.movement"
            implementationClass = "me.whereareiam.anvil.gradle.unit.MovementPlugin"
            displayName = "Anvil Movement Capability"
            description = "Includes the built-in Movement capability"
        }
        create("anvilCapabilityInventory") {
            id = "me.whereareiam.anvil.capability.inventory"
            implementationClass = "me.whereareiam.anvil.gradle.unit.InventoryPlugin"
            displayName = "Anvil Inventory Capability"
            description = "Includes the built-in Inventory capability"
        }
        create("anvilCapabilityInteraction") {
            id = "me.whereareiam.anvil.capability.interaction"
            implementationClass = "me.whereareiam.anvil.gradle.unit.InteractionPlugin"
            displayName = "Anvil Interaction Capability"
            description = "Includes the built-in Interaction capability"
        }
        create("anvilPlatformPaper") {
            id = "me.whereareiam.anvil.platform.paper"
            implementationClass = "me.whereareiam.anvil.gradle.unit.PaperPlugin"
            displayName = "Anvil Paper Platform"
            description = "Includes the Paper platform provider and agent"
        }
        create("anvilPlatformSpigot") {
            id = "me.whereareiam.anvil.platform.spigot"
            implementationClass = "me.whereareiam.anvil.gradle.unit.SpigotPlugin"
            displayName = "Anvil Spigot Platform"
            description = "Includes the Spigot platform provider and agent"
        }
        create("anvilPlatformVelocity") {
            id = "me.whereareiam.anvil.platform.velocity"
            implementationClass = "me.whereareiam.anvil.gradle.unit.VelocityPlugin"
            displayName = "Anvil Velocity Platform"
            description = "Includes the Velocity platform provider and agent"
        }
        create("anvilPlatformBungeeCord") {
            id = "me.whereareiam.anvil.platform.bungeecord"
            implementationClass = "me.whereareiam.anvil.gradle.unit.BungeeCordPlugin"
            displayName = "Anvil BungeeCord Platform"
            description = "Includes the BungeeCord platform provider and agent"
        }
    }
}

toolkitPublish {
    name.set("pluginMaven")
    artifactId.set("anvil-gradle-plugin")
    pom {
        name.set("Anvil Gradle Plugin")
        description.set(project.description)
    }
}

tasks.named("publishToMavenLocal") {
    dependsOn(
        ":anvil-integration:junit:gradle:publishToMavenLocal",
        ":anvil-tooling:gradle:scenarios:publishToMavenLocal",
        ":anvil-tooling:tooling-runner:publishToMavenLocal",
    )
}
