package me.whereareiam.anvil.api.model.process;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Immutable declaration of one Minecraft proxy and the servers registered behind it.
 */
@Value
@Builder(toBuilder = true)
public class MinecraftProxy implements MinecraftProcess {
	@NotNull String name;
	@NotNull String platform;
	@NotNull Distribution distribution;

	@Builder.Default
	boolean onlineMode = false;

	@Builder.Default
	int memoryMegabytes = 512;

	@NotNull
	@Builder.Default
	WorkspacePlan workspace = WorkspacePlan.builder().build();

	@NotNull
	@Singular("setting")
	Map<String, String> settings;

	@NotNull
	@Singular("jvmArgument")
	List<String> jvmArguments;

	@NotNull
	@Singular("server")
	List<String> servers;

	@NotNull String defaultServer;
}
