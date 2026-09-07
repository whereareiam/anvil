package me.whereareiam.anvil.api.model.process;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Immutable declaration of one direct or proxy-backed Minecraft server process.
 */
@Value
@Builder(toBuilder = true)
public class MinecraftServer implements MinecraftProcess {
	@NotNull String name;
	@NotNull String platform;
	@NotNull Distribution distribution;

	/**
	 * Process Java override; null inherits the scenario requirement.
	 */
	@Nullable JavaRequirement javaRequirement;

	/**
	 * Process Java source; null inherits the scenario source.
	 */
	@Nullable JavaSource javaSource;

	@Nullable String minecraftVersion;

	@Builder.Default
	boolean onlineMode = false;

	@Builder.Default
	int memoryMegabytes = 1024;

	@NotNull
	@Builder.Default
	WorkspacePlan workspace = WorkspacePlan.builder().build();

	@NotNull
	@Singular("setting")
	Map<String, String> settings;

	@NotNull
	@Singular("jvmArgument")
	List<String> jvmArguments;
}
