package me.whereareiam.anvil.workbench.protocol.model.java;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Inspects an executable from the properties it printed.
 */
@Value
public class InspectJava implements JavaRequest<InspectedJava> {
	/**
	 * Output of the executable's settings.
	 */
	@NotNull String properties;

	/**
	 * The executable on the workbench.
	 */
	@NotNull Path executable;

	@Override
	public @NotNull Class<InspectedJava> responseType() {
		return InspectedJava.class;
	}
}
