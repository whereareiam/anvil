import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
	id("org.jetbrains.intellij.platform")
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

description = "IntelliJ IDEA helper plugin for Anvil"

architecture {
	kind = assembly
}

dependencies {
	implementation(projects.anvilApi)
	implementation(projects.anvilTooling.toolingApi)
	implementation(projects.anvilIntegration.integrationIntellij.intellijApi)
	implementation(projects.anvilIntegration.integrationIntellij.intellijEngine)
	implementation(projects.anvilIntegration.integrationIntellij.intellijGradle)
	implementation(projects.anvilIntegration.integrationIntellij.intellijUi)
}

// Native instrumentation tasks share the project's Ant builder.
tasks.named("instrumentTestCode") {
	mustRunAfter(tasks.named("instrumentCode"))
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
	from(rootProject.file("assets/branding/anvil-transparent.svg")) {
		into("icons")
		rename { "anvil.svg" }
	}
}

val verificationIdePath = providers.gradleProperty("anvil.intellijVerificationPath")

intellijPlatformTesting.testIde.register("testCurrentIde") {
	type = IntelliJPlatformType.IntellijIdeaUltimate
	version = libs.versions.intellij.verify
	if (verificationIdePath.isPresent)
		localPath.set(file(verificationIdePath.get()))
	// Test against the selected runtime's framework while compiling against the baseline SDK.
	testFramework(TestFrameworkType.Platform, task.map { it.productInfo.buildNumber })
	plugins {
		bundledPlugins("com.intellij.java", "org.jetbrains.plugins.gradle")
		// The flat test loader cannot isolate duplicate obfuscated product classes in this optional plugin.
		disablePlugin("com.intellij.modules.ultimate")
	}
	task {
		useJUnitPlatform()
		classpath += sourceSets.test.get().output
	}
}

dependencies {
	testImplementation(libs.junit4)
	testRuntimeOnly(libs.junit.vintage)

	intellijPlatform {
		create("IC", libs.versions.intellij.idea)
		bundledPlugin("com.intellij.java")
		bundledPlugin("org.jetbrains.plugins.gradle")
		testFramework(TestFrameworkType.Platform)
	}
}

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
