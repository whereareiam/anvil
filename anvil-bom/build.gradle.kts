import me.whereareiam.toolkit.architecture.ArchitecturePublications

plugins {
    `java-platform`
    alias(libs.plugins.toolkit.publish.maven)
}

description = "Bill of materials for Anvil modules"

toolkitPublish {
    component.set("javaPlatform")
    artifactId.set("bom")
    pom {
        name.set("Anvil BOM")
        description.set(project.description)
    }
}

javaPlatform {
    allowDependencies()
}

val projectToolingModules = listOf(
	":anvil-integration:integration-gradle:gradle-artifacts",
	":anvil-integration:integration-gradle:gradle-tooling",
)

gradle.projectsEvaluated {
    dependencies {
        constraints {
            (ArchitecturePublications.publishedProjects(rootProject) + projectToolingModules.map(rootProject::project))
                .distinctBy { it.path }
                .forEach { module ->
                api(project(module.path))
                module.extensions.getByType<PublishingExtension>().publications
                    .withType<MavenPublication>()
                    .filter { it.artifacts.isEmpty() }
                    .forEach { publication ->
                        api("${publication.groupId}:${publication.artifactId}:${publication.version}")
                    }
            }
        }
    }
}
