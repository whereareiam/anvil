package me.whereareiam.anvil.integration.intellij.exception;

import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSyncExceptionTest {
	@Test
	void preservesIntegrationSummaryInsteadOfUnwrappingToAnEmptyRootCause() {
		var cause = new IllegalStateException("");
		var detailed =
				new ProjectSyncException(
						"Project model import failed", "The source set is missing.", cause);
		var restored = ProjectSyncException.from(new CompletionException(detailed));

		assertSame(detailed, restored);
		assertSame(cause, restored.getCause());
		assertEquals("Project model import failed", restored.getMessage());
	}

	@Test
	void replacesBareExceptionTypeWithActionableSummaryAndPreservesItsDetails() {
		var detailed =
				ProjectSyncException.from(
						new CompletionException(
								new IllegalStateException(
										"com.intellij.openapi.externalSystem.SomePartialResolutionException: ")));

		assertFalse(detailed.getMessage().contains("com.intellij"));
		assertTrue(detailed.getMessage().contains("View sync details"));
		assertTrue(detailed.getDetails().contains("SomePartialResolutionException"));
	}
}
