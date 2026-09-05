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

gradle.projectsEvaluated {
    dependencies {
        constraints {
            ArchitecturePublications.publishedProjects(rootProject).forEach { module ->
                api(project(module.path))
            }
        }
    }
}
