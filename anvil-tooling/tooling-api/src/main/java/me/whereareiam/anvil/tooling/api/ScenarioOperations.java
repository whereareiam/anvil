package me.whereareiam.anvil.tooling.api;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import lombok.experimental.UtilityClass;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioLaunchRequest;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.ToolingOperation;
import org.jetbrains.annotations.NotNull;

/**
 * Shared scenario operation contracts consumed by tooling clients and runner handlers.
 */
@UtilityClass
public class ScenarioOperations {
	/**
	 * Lists definitions without starting an environment.
	 */
	public static final @NotNull ToolingOperation<Void, List<ScenarioDescriptor>> DISCOVER =
			ToolingOperation.<Void, List<ScenarioDescriptor>>builder()
					.name("scenarios")
					.requestType(Void.class)
					.responseType(new TypeReference<>() {})
					.build();

	/**
	 * Selects a scenario and starts its requested initial processes.
	 */
	public static final @NotNull ToolingOperation<ScenarioLaunchRequest, SessionSnapshot> START =
			ToolingOperation.<ScenarioLaunchRequest, SessionSnapshot>builder()
					.name("start")
					.requestType(ScenarioLaunchRequest.class)
					.responseType(new TypeReference<>() {})
					.build();
}
