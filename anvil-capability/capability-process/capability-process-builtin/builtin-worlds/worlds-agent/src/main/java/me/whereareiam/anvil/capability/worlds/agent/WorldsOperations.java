package me.whereareiam.anvil.capability.worlds.agent;

import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentLogRequest;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacements;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentTransfer;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChanges;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldCreation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldEvents;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldList;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldLoad;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldOutcome;
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
	public static final @NotNull AgentOperation<AgentLogRequest, AgentWorldChanges> CHANGES =
			AgentOperation.<AgentLogRequest, AgentWorldChanges>builder()
					.name(NAMESPACE + ".changes").requestType(AgentLogRequest.class)
					.responseType(AgentWorldChanges.class).build();
	/**
	 * Creates a world, answering the world or why it was not created.
	 */
	public static final @NotNull AgentOperation<AgentWorldCreation, AgentWorldOutcome> CREATE =
			AgentOperation.<AgentWorldCreation, AgentWorldOutcome>builder()
					.name(NAMESPACE + ".create").requestType(AgentWorldCreation.class)
					.responseType(AgentWorldOutcome.class).build();
	/**
	 * Loads a world from the folder the server keeps for it, answering the world or why it was not loaded.
	 */
	public static final @NotNull AgentOperation<AgentWorldLoad, AgentWorldOutcome> LOAD =
			AgentOperation.<AgentWorldLoad, AgentWorldOutcome>builder()
					.name(NAMESPACE + ".load").requestType(AgentWorldLoad.class)
					.responseType(AgentWorldOutcome.class).build();
	/**
	 * Unloads the world of a key, answering whether it was unloaded or why not.
	 */
	public static final @NotNull AgentOperation<String, AgentWorldOutcome> UNLOAD =
			AgentOperation.<String, AgentWorldOutcome>builder()
					.name(NAMESPACE + ".unload").requestType(String.class).responseType(AgentWorldOutcome.class).build();
	/**
	 * Unloads the world of a key without saving it and deletes its folder, answering whether it was deleted or why
	 * not.
	 */
	public static final @NotNull AgentOperation<String, AgentWorldOutcome> DELETE =
			AgentOperation.<String, AgentWorldOutcome>builder()
					.name(NAMESPACE + ".delete").requestType(String.class).responseType(AgentWorldOutcome.class).build();
	/**
	 * Reads the worlds the server loaded and unloaded after the events the host has read.
	 */
	public static final @NotNull AgentOperation<AgentLogRequest, AgentWorldEvents> EVENTS =
			AgentOperation.<AgentLogRequest, AgentWorldEvents>builder()
					.name(NAMESPACE + ".events").requestType(AgentLogRequest.class)
					.responseType(AgentWorldEvents.class).build();

	private WorldsOperations() {
	}
}
