package me.whereareiam.anvil.platform.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Executable distribution resolved and integrity-checked by a {@link PlatformProvider}.
 */
@Value
@Builder
public class ResolvedDistribution {
	@NotNull Path jar;
	@NotNull String description;
}
