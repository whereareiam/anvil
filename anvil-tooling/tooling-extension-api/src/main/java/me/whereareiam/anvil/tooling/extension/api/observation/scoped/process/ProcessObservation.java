package me.whereareiam.anvil.tooling.extension.api.observation.scoped.process;

import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDefinition;
import me.whereareiam.anvil.tooling.extension.api.observation.ToolingObservation;
import org.jetbrains.annotations.NotNull;

/**
 * An observation evaluated against a current process target.
 */
public abstract class ProcessObservation extends ToolingObservation<RunningProcess> {
	/**
	 * Declares this process contribution.
	 *
	 * @param definition portable identity and presentation
	 */
	protected ProcessObservation(@NotNull ObservationDefinition definition) {
		super(definition);
	}
}
