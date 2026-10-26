import me.whereareiam.anvil.buildlogic.capability.CapabilityFamiliesArgument

plugins {
	`kotlin-dsl`
	id("module-gradle-plugin")
}

description = "Gradle adapters for Anvil capability providers"

dependencies {
	implementation(projects.anvilIntegration.integrationGradle.gradleBase)
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
		create("anvilCapabilityDispatch") {
			id = "me.whereareiam.anvil.capability.dispatch"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.DispatchPlugin"
			displayName = "Anvil Dispatch Capability"
			description = "Includes the built-in Dispatch capability"
		}
		create("anvilCapabilityPlayers") {
			id = "me.whereareiam.anvil.capability.players"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.PlayersPlugin"
			displayName = "Anvil Players Capability"
			description = "Includes the built-in Players capability"
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
		create("anvilCapabilityCommands") {
			id = "me.whereareiam.anvil.capability.commands"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.CommandsPlugin"
			displayName = "Anvil Commands Capability"
			description = "Includes the built-in Commands capability"
		}
		create("anvilCapabilityCookies") {
			id = "me.whereareiam.anvil.capability.cookies"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.CookiesPlugin"
			displayName = "Anvil Cookies Capability"
			description = "Includes the built-in Cookies capability"
		}
		create("anvilCapabilityDialogs") {
			id = "me.whereareiam.anvil.capability.dialogs"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.DialogsPlugin"
			displayName = "Anvil Dialogs Capability"
			description = "Includes the built-in Dialogs capability"
		}
		create("anvilCapabilityScreen") {
			id = "me.whereareiam.anvil.capability.screen"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.ScreenPlugin"
			displayName = "Anvil Screen Capability"
			description = "Includes the built-in Screen capability"
		}
		create("anvilCapabilitySounds") {
			id = "me.whereareiam.anvil.capability.sounds"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.SoundsPlugin"
			displayName = "Anvil Sounds Capability"
			description = "Includes the built-in Sounds capability"
		}
		create("anvilCapabilityEntities") {
			id = "me.whereareiam.anvil.capability.entities"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.EntitiesPlugin"
			displayName = "Anvil Entities Capability"
			description = "Includes the built-in Entities capability"
		}
		create("anvilCapabilityWorld") {
			id = "me.whereareiam.anvil.capability.world"
			implementationClass = "me.whereareiam.anvil.integration.gradle.capability.WorldPlugin"
			displayName = "Anvil World Capability"
			description = "Includes the built-in World capability"
		}
	}
}

// Each built-in capability family publishes its bundle as builtin-<family>. The plugin test receives the family
// names and checks that every family has a plugin installing that bundle.
tasks.named<Test>("test") {
	jvmArgumentProviders.add(objects.newInstance<CapabilityFamiliesArgument>().apply {
		capabilityDirectory.set(layout.settingsDirectory.dir("anvil-capability"))
	})
}
