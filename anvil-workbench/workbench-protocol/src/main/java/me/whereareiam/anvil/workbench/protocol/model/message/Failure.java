package me.whereareiam.anvil.workbench.protocol.model.message;

import lombok.Value;
import me.whereareiam.anvil.workbench.protocol.type.WireFailure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A failure on the workbench, as the engine recreates it.
 */
@Value
public class Failure {
	/**
	 * Kind of failure.
	 */
	@NotNull WireFailure kind;

	/**
	 * Its message.
	 */
	@NotNull String message;

	/**
	 * Missing file, for that kind.
	 */
	@Nullable String file;

	/**
	 * Failed process, for that kind.
	 */
	@Nullable String processName;

	/**
	 * Refused scenario, for that kind.
	 */
	@Nullable String scenarioName;

	/**
	 * Refusing workbench, for that kind.
	 */
	@Nullable String workbenchName;

	/**
	 * Unmet requirements, for that kind.
	 */
	@NotNull List<String> unmet;
}
