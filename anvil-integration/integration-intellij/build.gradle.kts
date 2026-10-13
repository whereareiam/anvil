plugins {
	// The feature modules write their views in Kotlin. Gradle loads a plugin once per class loader, and this
	// project's conventions put the Kotlin plugin on the class loader the feature modules inherit.
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlin.lombok) apply false
	id("module-java")
	id("test-intellij")
}

description = "IntelliJ IDEA plugin for Anvil, assembled from the integration's feature modules"

// Ships to the JetBrains Marketplace rather than through Maven publication.
architecture {
	kind = assembly
	// IntelliJ is a frontend for tooling, with IDE-specific contracts in intellij-api.
	family = projects.anvilTooling.path
}

intellijPlatformModule {
	bundledPlugins.add("org.jetbrains.plugins.gradle")
}

dependencies {
	implementation(projects.anvilIntegration.integrationIntellij.intellijAccount)
	implementation(projects.anvilIntegration.integrationIntellij.intellijApi)
	implementation(projects.anvilIntegration.integrationIntellij.intellijCommon)
	implementation(projects.anvilIntegration.integrationIntellij.intellijGradle)
	implementation(projects.anvilIntegration.integrationIntellij.intellijScenario)

	testImplementation(projects.anvilIntegration.integrationIntellij.intellijApi)
}

tasks.processResources {
	from(rootProject.file("assets/branding/anvil-light.svg")) {
		into("META-INF")
		rename { "pluginIcon.svg" }
	}
	from(rootProject.file("assets/branding/anvil-dark.svg")) {
		into("META-INF")
		rename { "pluginIcon_dark.svg" }
	}
}

// The plugin's own tests check its composition; each feature module's tests run with it.
tasks.test {
	dependsOn(subprojects.map { "${it.path}:test" })
}

tasks.named("build") {
	dependsOn(subprojects.map { "${it.path}:build" })
}

val verificationIdePath = providers.gradleProperty("anvil.intellijVerificationPath")

intellijPlatform {
	projectName = "anvil-intellij"
	buildSearchableOptions = false

	pluginConfiguration {
		id = "me.whereareiam.anvil"
		name = "Anvil"
		version = project.version.toString()
		ideaVersion {
			sinceBuild = "252"
			untilBuild = "262.*"
		}
		changeNotes = providers.fileContents(layout.projectDirectory.file("marketplace/change-notes.html")).asText
		vendor {
			name = "whereareiam"
			url = "https://github.com/whereareiam"
		}
	}

	// Release publication supplies these values from repository secrets; local builds leave them unset.
	signing {
		certificateChain = providers.environmentVariable("INTELLIJ_CERTIFICATE_CHAIN")
		privateKey = providers.environmentVariable("INTELLIJ_PRIVATE_KEY")
		password = providers.environmentVariable("INTELLIJ_PRIVATE_KEY_PASSWORD")
	}

	publishing {
		token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
		// Pre-release versions such as 1.0.0-beta.1 go to the EAP channel; plain versions are stable.
		channels = listOf(if (project.version.toString().contains('-')) "eap" else "default")
	}

	pluginVerification {
		ides {
			create("IC", libs.versions.intellij.idea)
			if (verificationIdePath.isPresent)
				local(verificationIdePath)
			else
				create("IU", libs.versions.intellij.verify)
		}
	}
}
