package me.whereareiam.anvil.integration.intellij.gradle.model;

import com.intellij.serialization.ObjectSerializer;
import com.intellij.serialization.ReadConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportedModuleTest {
	@Test
	void survivesTheExternalSystemCacheRoundTripUsedAcrossIdeRestarts() {
		ImportedModule module = ImportedModule.builder()
				.enabled(true)
				.incompatible(false)
				.moduleDirectory("/work/app")
				.buildRootDirectory("/work")
				.projectPath(":app")
				.executionDirectory("/work")
				.preparationTaskPath(":app:anvilTooling")
				.build();
		ObjectSerializer serializer = ObjectSerializer.Companion.getInstance();

		ImportedModule restored = serializer.read(ImportedModule.class, serializer.writeAsBytes(module), new ReadConfiguration());

		assertEquals(ImportedModule.SCHEMA_VERSION, restored.getSchemaVersion());
		assertTrue(restored.isEnabled());
		assertEquals("/work/app", restored.getModuleDirectory());
		assertEquals("/work", restored.getBuildRootDirectory());
		assertEquals(":app", restored.getProjectPath());
		assertEquals("/work", restored.getExecutionDirectory());
		assertEquals(":app:anvilTooling", restored.getPreparationTaskPath());
	}
}
