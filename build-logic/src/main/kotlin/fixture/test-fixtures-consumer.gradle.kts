import me.whereareiam.anvil.buildlogic.fixture.FixtureInputs

plugins {
	java
}

extensions.create("fixtures", FixtureInputs::class.java, project)

val supportPath = ":anvil-testkit:support"
if (project.path != supportPath) {
	dependencies {
		testImplementation(project(supportPath))
	}
}
