package me.whereareiam.anvil.player.library.mcprotocol.model.worker;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.api.model.player.PayloadChannel;
import me.whereareiam.anvil.api.model.player.PlayerClient;
import me.whereareiam.anvil.api.type.ClientLoader;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Client a worker player presents itself as: its mod loader and the payload channels it claims, none for a vanilla
 * client.
 */
@Value
@Builder
@Jacksonized
public class WorkerClient {
	@NotNull ClientLoader loader;
	@NotNull List<WorkerChannel> channels;

	/**
	 * Carries a player's client to its worker.
	 *
	 * @param client the client the player presents itself as
	 * @return the same client as the worker reads it
	 */
	public static @NotNull WorkerClient of(@NotNull PlayerClient client) {
		List<WorkerChannel> channels = client.getChannels().stream()
				.map(channel -> WorkerChannel.builder()
						.id(channel.getId())
						.version(channel.getVersion())
						.phase(channel.getPhase())
						.flow(channel.getFlow())
						.optional(channel.isOptional())
						.build())
				.toList();

		return WorkerClient.builder().loader(client.getLoader()).channels(channels).build();
	}

	/**
	 * Returns the client this worker player presents itself as.
	 *
	 * @return the player's client
	 */
	public @NotNull PlayerClient toClient() {
		if (loader == ClientLoader.VANILLA) return PlayerClient.vanilla();

		return PlayerClient.neoforge(channels.stream()
				.map(channel -> PayloadChannel.builder()
						.id(channel.getId())
						.version(channel.getVersion())
						.phase(channel.getPhase())
						.flow(channel.getFlow())
						.optional(channel.isOptional())
						.build())
				.toList());
	}
}
