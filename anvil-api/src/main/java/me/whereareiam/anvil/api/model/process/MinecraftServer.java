package me.whereareiam.anvil.api.model.process;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.java.JavaSelection;
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

	/**
	 * Optional labels for tooling; the process name remains its routing identity.
	 */
	@Nullable PresentationMetadata metadata;

	@NotNull String platform;
	@NotNull Distribution distribution;

	/**
	 * Process-specific Java selection; omitted members inherit from the scenario and engine.
	 */
	@NotNull
	@Builder.Default
	JavaSelection javaSelection = JavaSelection.builder().build();

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
