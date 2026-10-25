package me.whereareiam.anvil.workbench.protocol;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioRequirementsException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.workbench.api.exception.WorkbenchException;
import me.whereareiam.anvil.workbench.protocol.model.message.Failure;
import me.whereareiam.anvil.workbench.protocol.type.WireFailure;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.List;

/**
 * Carries a failure on the workbench to the engine as a {@link WireFailure}, so the engine throws the exception it
 * would have seen locally.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WireErrors {
	/**
	 * Describes a failure on the workbench for the engine.
	 *
	 * @param failure failure on the workbench
	 * @return its kind, message and the members its kind carries
	 */
	public static @NotNull Failure describe(@NotNull Throwable failure) {
		Throwable described = failure instanceof UncheckedIOException unchecked ? unchecked.getCause() : failure;
		String message = described.getMessage() == null ? described.getClass().getSimpleName() : described.getMessage();
		return switch (described) {
			case NoSuchFileException missing -> new Failure(WireFailure.MISSING_FILE, message, missing.getFile(), null, null, null, List.of());
			case ProcessException process -> new Failure(WireFailure.PROCESS, message, null, process.getProcessName(), null, null, List.of());
			case ScenarioRequirementsException refused -> new Failure(WireFailure.REQUIREMENTS, message, null, null,
					refused.getScenarioName(), refused.getWorkbenchName(), refused.getUnmet());
			default -> new Failure(kind(described), message, null, null, null, null, List.of());
		};
	}

	/**
	 * Recreates a described failure on the engine, as an unchecked exception or an {@link IOException}.
	 *
	 * @param error described failure
	 * @return the exception its kind stands for
	 */
	public static @NotNull Exception restore(@NotNull Failure failure) {
		String message = failure.getMessage();
		return switch (failure.getKind()) {
			case MISSING_FILE -> new NoSuchFileException(failure.getFile() == null ? message : failure.getFile());
			case IO -> new IOException(message);
			case INVALID_ARGUMENT -> new IllegalArgumentException(message);
			case INVALID_STATE -> new IllegalStateException(message);
			case UNSUPPORTED -> new UnsupportedOperationException(message);
			case SCENARIO_VALIDATION -> new ScenarioValidationException(message);
			case PROVISIONING -> new ProvisioningException(message);
			case PROCESS -> new ProcessException(String.valueOf(failure.getProcessName()), message);
			case REQUIREMENTS -> new ScenarioRequirementsException(String.valueOf(failure.getScenarioName()),
					String.valueOf(failure.getWorkbenchName()), failure.getUnmet());
			case WORKBENCH -> new WorkbenchException(message);
		};
	}

	/**
	 * The kind a failure is, its most specific one first: a missing file is an I/O failure too.
	 */
	private static WireFailure kind(Throwable failure) {
		return switch (failure) {
			case NoSuchFileException ignored -> WireFailure.MISSING_FILE;
			case IOException ignored -> WireFailure.IO;
			case ScenarioRequirementsException ignored -> WireFailure.REQUIREMENTS;
			case ScenarioValidationException ignored -> WireFailure.SCENARIO_VALIDATION;
			case ProvisioningException ignored -> WireFailure.PROVISIONING;
			case ProcessException ignored -> WireFailure.PROCESS;
			case UnsupportedOperationException ignored -> WireFailure.UNSUPPORTED;
			case IllegalArgumentException ignored -> WireFailure.INVALID_ARGUMENT;
			case IllegalStateException ignored -> WireFailure.INVALID_STATE;
			default -> WireFailure.WORKBENCH;
		};
	}
}
