package me.whereareiam.anvil.capability.worlds.agent;

import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacements;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentTransfer;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChanges;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChangesRequest;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldList;
import org.jetbrains.annotations.NotNull;

/**
 * The agent channel of the worlds capability: the operations its host provider requests and every platform side
 * answers, in the namespace the platform sides own.
 */
public final class WorldsOperations {
	/**
	 * The namespace the operations belong to, which the platform sides own.
	 */
	public static final @NotNull String NAMESPACE = "me.whereareiam.anvil.worlds";
	/**
	 * Lists the worlds the server has loaded.
	 */
	public static final @NotNull AgentOperation<Void, AgentWorldList> WORLDS = AgentOperation.<Void, AgentWorldList>builder()
			.name(NAMESPACE + ".worlds").requestType(Void.class).responseType(AgentWorldList.class).build();
	/**
	 * Tells where each player on the server is.
	 */
	public static final @NotNull AgentOperation<Void, AgentPlacements> PLACEMENTS = AgentOperation.<Void, AgentPlacements>builder()
			.name(NAMESPACE + ".placements").requestType(Void.class).responseType(AgentPlacements.class).build();
	/**
	 * Moves a player to a location, answering whether the server moved it.
	 */
	public static final @NotNull AgentOperation<AgentTransfer, Boolean> SEND = AgentOperation.<AgentTransfer, Boolean>builder()
			.name(NAMESPACE + ".send").requestType(AgentTransfer.class).responseType(Boolean.class).build();
	/**
	 * Reads the world changes the server reported after the ones the host has read.
	 */
	public static final @NotNull AgentOperation<AgentWorldChangesRequest, AgentWorldChanges> CHANGES =
			AgentOperation.<AgentWorldChangesRequest, AgentWorldChanges>builder()
					.name(NAMESPACE + ".changes").requestType(AgentWorldChangesRequest.class)
					.responseType(AgentWorldChanges.class).build();

	private WorldsOperations() {
	}
}
