package me.whereareiam.anvil.platform.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import org.jetbrains.annotations.NotNull;

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
	 * Effective Java selection after process, scenario, and engine inheritance. Its requirement
	 * always carries the exact LTS feature version planning selected from the platform's Java row;
	 * the source may remain unspecified for execution to select.
	 */
	@NotNull JavaSelection javaSelection;

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

	boolean agent;
	@NotNull Pattern readinessPattern;
	@NotNull String stopCommand;
	/**
	 * JVM arguments selected during planning, before explicit declaration arguments: the platform's
	 * Java maximum bypass property when the requested Java version needs it, then platform defaults.
	 */
	@NotNull
	@Singular
	List<String> jvmArguments;
	@NotNull
	@Singular
	List<String> programArguments;
	@NotNull
	@Singular
	List<WorkspaceCache> defaultCaches;
}
