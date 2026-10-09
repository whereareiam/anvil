import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.lombok)
	id("jvm")
	id("intellij-platform-tests")
}

description = "IntelliJ presentation and platform adapters for Anvil"

intellijPlatformModule {
	bundledPlugins.add("org.jetbrains.plugins.gradle")
}

// The baseline IDE supplies Kotlin 2.1; do not emit calls requiring a newer runtime.
kotlin {
	compilerOptions {
		apiVersion.set(KotlinVersion.KOTLIN_2_1)
		languageVersion.set(KotlinVersion.KOTLIN_2_2)
	}
}

dependencies {
	implementation(projects.anvilApi)
	implementation(projects.anvilIntegration.integrationIntellij.intellijApi)

	compileOnly(projects.anvilTooling.toolingApi)

	testImplementation(projects.anvilIntegration.integrationIntellij.intellijEngine)
	testImplementation(testFixtures(projects.anvilIntegration.integrationIntellij.intellijEngine))
	testImplementation(projects.anvilTooling.toolingApi)
}

// Native tests supply presentation resources without depending on the packaging project.
tasks.processTestResources {
	from(rootProject.file("assets/branding/anvil-transparent.svg")) {
		into("icons")
		rename { "anvil.svg" }
	}
}

// Screenshot capture is opt-in: add -Panvil.uiCaptures to write test renders under build/reports.
if (providers.gradleProperty("anvil.uiCaptures").isPresent) {
	val captures = layout.buildDirectory.dir("reports/ui-captures").get().asFile.path
	tasks.test {
		systemProperty("anvil.uiCaptures", captures)
	}
}
