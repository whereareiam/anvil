package me.whereareiam.anvil.workbench.protocol.model.workbench;

import lombok.Value;
import me.whereareiam.anvil.workbench.api.model.WorkbenchFacts;
import org.jetbrains.annotations.NotNull;

/**
 * Asks for the workbench's current facts under its operator's policy.
 */
@Value
public class ReadFacts implements WorkbenchSetupRequest<WorkbenchFacts> {
	@Override
	public @NotNull Class<WorkbenchFacts> responseType() {
		return WorkbenchFacts.class;
	}
}
