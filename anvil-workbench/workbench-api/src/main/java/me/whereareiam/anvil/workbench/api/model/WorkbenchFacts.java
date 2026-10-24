package me.whereareiam.anvil.workbench.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a workbench can offer a scenario at one moment: its capacity, its execution providers and the hosts its
 * processes may contact. A scenario's requirements are checked against these facts before it is prepared.
 */
@Value
@Builder(toBuilder = true)
public class WorkbenchFacts {
	/**
	 * Name of the workbench the facts describe.
	 */
	@NotNull String workbenchName;

	/**
	 * Whether the workbench shares the engine's machine, so that paths and installations on the engine's machine are
	 * its own.
	 */
	boolean local;

	/**
	 * Processors the workbench's processes may use together.
	 */
	int processors;

	/**
	 * Memory the workbench's processes may use together, in MiB.
	 */
	int memoryMegabytes;

	/**
	 * Identifiers of the installed execution providers, available or not.
	 */
	@NotNull
	@Singular
	Set<String> executions;

	/**
	 * Installed execution providers that cannot run processes now, with the reason for each.
	 */
	@NotNull
	@Singular
	Map<String, String> unavailableExecutions;

	/**
	 * Hosts outside the workbench its processes may contact. Each is a host name or a pattern whose leading
	 * {@code *.} matches any subdomain, such as {@code *.mojang.com}; {@code *} alone matches every host.
	 */
	@NotNull
	@Singular
	List<String> reachableHosts;
}
