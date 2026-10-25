package me.whereareiam.anvil.workbench.protocol.model.java;

import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaSource;
import org.jetbrains.annotations.NotNull;

/**
 * Inspects the installation a declared source names.
 */
@Value
public class InspectJavaSource implements JavaRequest<InspectedJava> {
	/**
	 * Declared source.
	 */
	@NotNull JavaSource source;

	@Override
	public @NotNull Class<InspectedJava> responseType() {
		return InspectedJava.class;
	}
}
