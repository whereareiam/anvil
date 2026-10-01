plugins {
	`kotlin-dsl`
	alias(libs.plugins.toolkit.architecture)
	id("gradle-plugin")
}

description = "Gradle adapters for Anvil capability providers"

dependencies {
	implementation(projects.anvilIntegration.integrationGradle.gradleBase)
	testImplementation(gradleTestKit())
}

gradlePlugin {
	plugins {
		create("anvilCapabilityDefault") {
			id = "me.whereareiam.anvil.capability.default"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.DefaultPlugin"
			displayName = "Anvil Default Capabilities"
			description = "Includes Anvil's default capability wiring"
		}
		create("anvilCapabilitySession") {
			id = "me.whereareiam.anvil.capability.session"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.SessionPlugin"
			displayName = "Anvil Session Capability"
			description = "Includes the built-in Session capability"
		}
		create("anvilCapabilityConsole") {
			id = "me.whereareiam.anvil.capability.console"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.ConsolePlugin"
			displayName = "Anvil Console Capability"
			description = "Includes the agent-owned Console capability"
		}
		create("anvilCapabilityServer") {
			id = "me.whereareiam.anvil.capability.server"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.ServerPlugin"
			displayName = "Anvil Server Capability"
			description = "Includes the built-in Server capability"
		}
		create("anvilCapabilityMessages") {
			id = "me.whereareiam.anvil.capability.messages"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.MessagesPlugin"
			displayName = "Anvil Messages Capability"
			description = "Includes the built-in Messages capability"
		}
		create("anvilCapabilityMovement") {
			id = "me.whereareiam.anvil.capability.movement"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.MovementPlugin"
			displayName = "Anvil Movement Capability"
			description = "Includes the built-in Movement capability"
		}
		create("anvilCapabilityInventory") {
			id = "me.whereareiam.anvil.capability.inventory"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.InventoryPlugin"
			displayName = "Anvil Inventory Capability"
			description = "Includes the built-in Inventory capability"
		}
		create("anvilCapabilityInteraction") {
			id = "me.whereareiam.anvil.capability.interaction"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.InteractionPlugin"
			displayName = "Anvil Interaction Capability"
			description = "Includes the built-in Interaction capability"
		}
	}
}

toolkitPublish {
	name.set("pluginMaven")
	artifactId.set("gradle-capabilities")
}
