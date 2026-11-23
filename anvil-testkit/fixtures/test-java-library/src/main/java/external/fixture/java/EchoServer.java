package external.fixture.java;

import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ServerboundChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundSetDefaultSpawnPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundSetTime;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundInitializeBorder;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderCenter;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDistance;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundForgetLevelChunk;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundSectionBlocksUpdate;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A release that carries chat, the packets of the world and the teleports and velocity of the player's own entity, and
 * a server that answers them: it puts a joining player in the overworld with one chunk column, whose lowest blocks are
 * stone, and answers chat as the player's chat and a command as a system message naming it.
 */
final class EchoServer {
	private static final Set<Class<? extends JavaPacket>> CARRIED = Set.of(
			ServerboundChat.class,
			ServerboundChatCommand.class,
			ClientboundPlayerChat.class,
			ClientboundSystemChat.class,
			ClientboundLogin.class,
			ClientboundRespawn.class,
			ClientboundPlayerPosition.class,
			ClientboundSetEntityMotion.class,
			ClientboundSetTime.class,
			ClientboundGameEvent.class,
			ClientboundSetDefaultSpawnPosition.class,
			ClientboundInitializeBorder.class,
			ClientboundSetBorderCenter.class,
			ClientboundSetBorderLerpSize.class,
			ClientboundSetBorderSize.class,
			ClientboundSetBorderWarningDelay.class,
			ClientboundSetBorderWarningDistance.class,
			ClientboundLevelChunkWithLight.class,
			ClientboundForgetLevelChunk.class,
			ClientboundBlockUpdate.class,
			ClientboundSectionBlocksUpdate.class
	);
	private static final String OVERWORLD = "minecraft:overworld";
	/**
	 * The global ID of stone's state in every release since Minecraft 1.13.
	 */
	private static final int STONE = 1;

	/**
	 * Tells whether the release carries a packet.
	 */
	boolean carries(@NotNull Class<? extends JavaPacket> type) {
		return CARRIED.contains(type);
	}

	/**
	 * Returns what the server sends a player that joins it: the level, and the chunk column at 0 0.
	 */
	@NotNull List<ClientboundPacket> join() {
		DimensionType overworld = DimensionType.known(OVERWORLD);
		List<LevelChunkSection> sections = new ArrayList<>(Collections.nCopies(overworld.height() / 16,
				LevelChunkSection.EMPTY));
		sections.set(0, LevelChunkSection.uniform(STONE));

		return List.of(
				ClientboundLogin.builder()
						.entityId(new EntityId(1))
						.level(OVERWORLD)
						.dimension(OVERWORLD)
						.dimensionType(overworld)
						.build(),
				new ClientboundLevelChunkWithLight(new ChunkPosition(0, 0), List.copyOf(sections))
		);
	}

	/**
	 * Returns what the server sends a player in answer to one of its packets.
	 */
	@NotNull List<ClientboundPacket> answer(@NotNull String player, @NotNull ServerboundPacket packet) {
		if (packet instanceof ServerboundChat chat)
			return List.of(ClientboundPlayerChat.unsigned(Text.literal("<" + player + "> " + chat.text())));
		if (packet instanceof ServerboundChatCommand command)
			return List.of(new ClientboundSystemChat(Text.literal("ran /" + command.command()), false));

		return List.of();
	}
}
