package me.whereareiam.anvil.workbench.protocol.model.java;

import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a Java installation for a requirement, downloading one when needed.
 */
@Value
public class ResolveJava implements JavaRequest<ResolvedJava> {
	/**
	 * Required Java.
	 */
	@NotNull JavaRequirement requirement;

	/**
	 * Declared source, or null.
	 */
	@Nullable JavaSource source;

	@Override
	public @NotNull Class<ResolvedJava> responseType() {
		return ResolvedJava.class;
	}
}
