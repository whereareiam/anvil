package me.whereareiam.anvil.api.model.java;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JavaSelectionTest {
	@Test
	void inheritsRequirementAndSourceIndependentlyAcrossThreeLevels() {
		var engineRequirement = JavaRequirement.builder().featureVersion(21).build();
		var scenarioRequirement = JavaRequirement.builder().featureVersion(25).build();
		var engineSource = JavaSource.home(Path.of("engine-jdk"));
		var processSource = JavaSource.home(Path.of("process-jdk"));
		var engine = JavaSelection.builder().requirement(engineRequirement).source(engineSource).build();
		var scenario = JavaSelection.builder().requirement(scenarioRequirement).build();
		var process = JavaSelection.builder().source(processSource).build();

		var effective = process.withDefaults(scenario).withDefaults(engine);

		assertSame(scenarioRequirement, effective.getRequirement());
		assertSame(processSource, effective.getSource());
		assertNull(process.getRequirement());
		assertNull(scenario.getSource());
	}

	@Test
	void explicitPlatformRequirementOverridesInheritedVersionWithoutResettingSource() {
		var source = JavaSource.home(Path.of("configured-jdk"));
		var defaults = JavaSelection.builder()
				.requirement(JavaRequirement.builder().featureVersion(25).build())
				.source(source)
				.build();
		var platformRequirement = JavaRequirement.builder().build();
		var declared = JavaSelection.builder().requirement(platformRequirement).build();

		var effective = declared.withDefaults(defaults);

		assertSame(platformRequirement, effective.getRequirement());
		assertSame(source, effective.getSource());
	}

	@Test
	void omittedSourceRemainsUnspecifiedUntilExecutionSelectsIt() {
		var selection = JavaSelection.builder().build();
		var effective = selection.withDefaults(JavaSelection.builder()
				.requirement(JavaRequirement.builder().build())
				.build());

		assertNotNull(effective.getRequirement());
		assertNull(effective.getSource());
	}
}
