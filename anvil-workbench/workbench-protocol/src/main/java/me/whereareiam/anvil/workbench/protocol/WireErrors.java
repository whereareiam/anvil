package me.whereareiam.anvil.workbench.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.exception.AnvilException;
import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioRequirementsException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.workbench.api.exception.WorkbenchException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

/**
 * Carries a failure on the workbench to the engine, so the engine sees the kind of failure it would see locally:
 * Anvil's own exceptions, the standard argument and state exceptions, and I/O failures keep their type.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WireErrors {
	/**
	 * Describes a failure for the engine.
	 *
	 * @param failure failure on the workbench
	 * @return its type, message and the members its type carries
	 */
	public static @NotNull ObjectNode describe(@NotNull Throwable failure) {
		Throwable described = failure instanceof UncheckedIOException unchecked ? unchecked.getCause() : failure;
		ObjectNode error = JsonNodeFactory.instance.objectNode()
				.put("type", described.getClass().getName())
				.put("message", described.getMessage() == null ? described.getClass().getSimpleName() : described.getMessage());
		if (described instanceof ProcessException process) error.put("processName", process.getProcessName());
		if (described instanceof NoSuchFileException missing) error.put("file", missing.getFile());
		if (described instanceof ScenarioRequirementsException refused) {
			error.put("scenarioName", refused.getScenarioName());
			error.put("workbenchName", refused.getWorkbenchName());
			refused.getUnmet().forEach(error.putArray("unmet")::add);
		}

		return error;
	}

	/**
	 * Recreates a described failure on the engine, as an unchecked exception or an {@link IOException}.
	 *
	 * @param error described failure
	 * @return the recreated failure
	 */
	public static @NotNull Exception restore(@NotNull JsonNode error) {
		String type = error.path("type").asText();
		String message = error.path("message").asText();
		return switch (type) {
			case "java.nio.file.NoSuchFileException" -> new NoSuchFileException(error.path("file").asText(message));
			case "java.io.IOException", "java.io.UncheckedIOException", "java.nio.file.FileSystemException" -> new IOException(message);
			case "java.lang.IllegalArgumentException" -> new IllegalArgumentException(message);
			case "java.lang.IllegalStateException" -> new IllegalStateException(message);
			case "java.lang.UnsupportedOperationException" -> new UnsupportedOperationException(message);
			case "me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException" -> new ScenarioValidationException(message);
			case "me.whereareiam.anvil.api.exception.ProvisioningException" -> new ProvisioningException(message);
			case "me.whereareiam.anvil.api.exception.ProcessException" ->
					new ProcessException(error.path("processName").asText(), message);
			case "me.whereareiam.anvil.api.exception.scenario.ScenarioRequirementsException" -> {
				List<String> unmet = new ArrayList<>();
				error.path("unmet").forEach(reason -> unmet.add(reason.asText()));
				yield new ScenarioRequirementsException(error.path("scenarioName").asText(), error.path("workbenchName").asText(), unmet);
			}
			default -> new WorkbenchException(type + ": " + message);
		};
	}

	/**
	 * Recreates a described failure and throws it unchecked, wrapping an {@link IOException}.
	 *
	 * @param error described failure
	 * @return never returns; declared so callers can write {@code throw WireErrors.unchecked(error)}
	 */
	public static @NotNull RuntimeException unchecked(@NotNull JsonNode error) {
		Exception restored = restore(error);
		if (restored instanceof RuntimeException runtime) return runtime;
		if (restored instanceof IOException io) return new UncheckedIOException(io);

		return new AnvilException(restored.getMessage(), restored);
	}
}
