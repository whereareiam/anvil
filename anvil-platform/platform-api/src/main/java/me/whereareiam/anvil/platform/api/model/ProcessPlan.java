package me.whereareiam.anvil.platform.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolved software, Java, topology, workspace, and readiness requirements for one declared process.
 * Forwarding settings are shared by all members of the same connected platform group.
 */
@Value
@Builder
public class ProcessPlan {
	@NotNull MinecraftProcess declaration;
	@NotNull WorkspacePlan workspace;
	@NotNull ForwardingConfiguration forwarding;

	/**
	 * Effective Java requirement selected from process, scenario, then engine declarations.
	 */
	@NotNull JavaRequirement javaRequirement;

	/**
	 * Effective explicit Java source, or null to let execution select its source.
	 */
	@Nullable JavaSource javaSource;

	/**
	 * Whether the declared process is a proxy rather than a server.
	 */
	boolean proxy;

	/**
	 * Whether execution should publish the game endpoint under the scenario network policy.
	 */
	boolean publishGame;

	/**
	 * Process names that must become ready before this process can start, in declaration order.
	 */
	@NotNull
	@Singular
	List<String> dependencies;

	int minimumJavaVersion;
	boolean agent;
	@NotNull Pattern readinessPattern;
	@NotNull String stopCommand;
	@NotNull
	@Singular
	List<String> programArguments;
	@NotNull
	@Singular
	List<WorkspaceCache> defaultCaches;
}
