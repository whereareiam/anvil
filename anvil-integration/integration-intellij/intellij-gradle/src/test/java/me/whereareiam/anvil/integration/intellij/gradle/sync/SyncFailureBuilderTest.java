package me.whereareiam.anvil.integration.intellij.gradle.sync;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.MessageEventImpl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncFailureBuilderTest {
	@Test
	void keepsLaterErrorWhenResourceWarningsFillTheDiagnosticBuffer() {
		var diagnostics = new SyncFailureBuilder();
		for (int index = 0; index < 40; index++)
			diagnostics.add(
					new MessageEventImpl(
							"build",
							MessageEvent.Kind.WARNING,
							"Resources",
							"Filter " + index,
							"Unsupported filter"));
		var error =
				new MessageEventImpl(
						"build",
						MessageEvent.Kind.ERROR,
						"Project model",
						"Cannot load project model",
						"Missing model dependency");
		diagnostics.add(error);
		var failure =
				diagnostics.build(
						"/project", "com.intellij.PartialResolutionException: ", "Native details", null);

		assertEquals("Cannot load project model", failure.getMessage());
		assertTrue(failure.getDetails().contains("Missing model dependency"));
		assertTrue(failure.getDetails().contains("Native details"));
		diagnostics.add(error);
		assertEquals(
				failure.getDetails(),
				diagnostics
						.build(
								"/project", "com.intellij.PartialResolutionException: ", "Native details", null)
						.getDetails());
	}

	@Test
	void boundsIndividualNativeMessagesAndDetails() {
		var diagnostics = new SyncFailureBuilder();
		diagnostics.add(
				new MessageEventImpl(
						"build",
						MessageEvent.Kind.ERROR,
						"Project model",
						"X".repeat(5000),
						"Y".repeat(20000)));
		var failure = diagnostics.build("/project", "Failed", null, null);

		assertTrue(failure.getMessage().length() <= 2049);
		assertFalse(failure.getDetails().contains("Y".repeat(17000)));
		assertTrue(failure.getDetails().contains("IDE Build output"));
	}
}
