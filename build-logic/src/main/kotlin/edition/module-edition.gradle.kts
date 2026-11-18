import me.whereareiam.anvil.buildlogic.edition.Editions
import me.whereareiam.toolkit.architecture.model.ArchitectureExtension
import me.whereareiam.toolkit.publish.maven.extension.ToolkitPublishExtension

plugins {
	id("module-api")
}

// A member of an edition, such as anvil-edition/edition-java/java-data/data-block: every member is an API of the one
// family its edition folder names, so the edition's protocol, data and physics use each other without grants. A member
// that exposes another family's API declares it itself, as the protocol and every member exporting it do for
// player-api, because permissions are not transitive. The folders are the published
// artifact ID, the edition's and the member's, such as edition-java-data-block; the ID is final once set, so a build
// file that sets its own fails instead of silently publishing other coordinates. module-defaults refuses a project
// below anvil-edition that does not apply this convention.
extensions.configure<ArchitectureExtension> {
	family = Editions.family(path)
}

extensions.configure<ToolkitPublishExtension> {
	artifactId.set(Editions.artifactId(path))
	artifactId.disallowChanges()
}
