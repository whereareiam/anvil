package me.whereareiam.anvil.protocol.java.api.packet;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDeleteChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.command.ClientboundCommands;
import me.whereareiam.anvil.protocol.java.api.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ClientboundCookieRequest;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ClientboundStoreCookie;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundClearDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundEntityPositionSync;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundMoveEntityPos;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundMoveEntityPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundMoveEntityRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundTeleportEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundSetEntityData;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ClientboundContainerClose;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ClientboundContainerSetContent;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ClientboundOpenScreen;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.player.ClientboundSetHeldSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.player.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.protocol.java.api.packet.overlay.ClientboundBossEvent;
import me.whereareiam.anvil.protocol.java.api.packet.overlay.ClientboundClearTitles;
import me.whereareiam.anvil.protocol.java.api.packet.overlay.ClientboundSetActionBarText;
import me.whereareiam.anvil.protocol.java.api.packet.overlay.ClientboundSetSubtitleText;
import me.whereareiam.anvil.protocol.java.api.packet.overlay.ClientboundSetTitleText;
import me.whereareiam.anvil.protocol.java.api.packet.overlay.ClientboundSetTitlesAnimation;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSoundEntity;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetDefaultSpawnPosition;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetTime;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundInitializeBorder;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderCenter;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderWarningDistance;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundForgetLevelChunk;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundSectionBlocksUpdate;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The catalog of every packet value of the Java protocol that a server sends, each under its kind: the packet class's
 * simple name, such as {@code ClientboundBlockUpdate}, which is unique among them because each names one packet of
 * the game.
 *
 * <p>Whatever writes packet values down and reads them back names them by kind through this catalog, so a value keeps
 * its kind when its class moves to another package: the worker channel that carries frames to the host and the files
 * that recordings of packet streams are kept in. A packet class is renamed only when the packet it stands for changes,
 * which is when written-down values of it stop being readable.</p>
 *
 * <p>Some packets are {@linkplain #isCostly(Class) costly}: decoding them takes far more than their bytes, and their
 * value holds nothing their bytes do not, such as a chunk column's sections. A release decodes them only while
 * something wants their class, so a frame carries their value only then.</p>
 *
 * <pre>{@code
 * String kind = ClientboundPackets.kind(ClientboundBlockUpdate.class); // "ClientboundBlockUpdate"
 * Class<? extends ClientboundPacket> type = ClientboundPackets.find(kind).orElseThrow();
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClientboundPackets {
	private static final Map<String, Class<? extends ClientboundPacket>> KINDS = index(List.of(
			ClientboundDeleteChat.class,
			ClientboundDisguisedChat.class,
			ClientboundPlayerChat.class,
			ClientboundSystemChat.class,
			ClientboundCommands.class,
			ClientboundRegistryData.class,
			ClientboundLogin.class,
			ClientboundRespawn.class,
			ClientboundStartConfiguration.class,
			ClientboundCookieRequest.class,
			ClientboundStoreCookie.class,
			ClientboundClearDialog.class,
			ClientboundShowDialog.class,
			ClientboundAddEntity.class,
			ClientboundRemoveEntities.class,
			ClientboundSetPassengers.class,
			ClientboundEntityPositionSync.class,
			ClientboundMoveEntityPos.class,
			ClientboundMoveEntityPosRot.class,
			ClientboundMoveEntityRot.class,
			ClientboundSetEntityMotion.class,
			ClientboundTeleportEntity.class,
			ClientboundRemoveMobEffect.class,
			ClientboundSetEntityData.class,
			ClientboundUpdateAttributes.class,
			ClientboundUpdateMobEffect.class,
			ClientboundPlayerCombatKill.class,
			ClientboundSetHealth.class,
			ClientboundContainerClose.class,
			ClientboundContainerSetContent.class,
			ClientboundContainerSetSlot.class,
			ClientboundOpenScreen.class,
			ClientboundSetHeldSlot.class,
			ClientboundSetPlayerInventory.class,
			ClientboundPlayerAbilities.class,
			ClientboundPlayerPosition.class,
			ClientboundBossEvent.class,
			ClientboundClearTitles.class,
			ClientboundSetActionBarText.class,
			ClientboundSetSubtitleText.class,
			ClientboundSetTitleText.class,
			ClientboundSetTitlesAnimation.class,
			ClientboundSound.class,
			ClientboundSoundEntity.class,
			ClientboundGameEvent.class,
			ClientboundSetDefaultSpawnPosition.class,
			ClientboundSetTime.class,
			ClientboundInitializeBorder.class,
			ClientboundSetBorderCenter.class,
			ClientboundSetBorderLerpSize.class,
			ClientboundSetBorderSize.class,
			ClientboundSetBorderWarningDelay.class,
			ClientboundSetBorderWarningDistance.class,
			ClientboundBlockUpdate.class,
			ClientboundForgetLevelChunk.class,
			ClientboundLevelChunkWithLight.class,
			ClientboundSectionBlocksUpdate.class
	));
	private static final Set<Class<? extends ClientboundPacket>> COSTLY = Set.of(ClientboundLevelChunkWithLight.class);

	/**
	 * Returns every packet class by its kind.
	 *
	 * @return the classes by kind, in the order of their areas
	 */
	public static @NotNull Map<String, Class<? extends ClientboundPacket>> kinds() {
		return KINDS;
	}

	/**
	 * Finds the packet class of a kind.
	 *
	 * @param kind a packet class's simple name, such as {@code ClientboundBlockUpdate}
	 * @return the class, or empty when no packet value has that kind
	 */
	public static @NotNull Optional<Class<? extends ClientboundPacket>> find(@NotNull String kind) {
		return Optional.ofNullable(KINDS.get(kind));
	}

	/**
	 * Returns the kind of a packet class.
	 *
	 * @param type the packet class
	 * @return its kind
	 * @throws IllegalArgumentException when the class is not one of the catalog's
	 */
	public static @NotNull String kind(@NotNull Class<? extends ClientboundPacket> type) {
		String kind = type.getSimpleName();
		if (KINDS.get(kind) != type)
			throw new IllegalArgumentException(type.getName() + " is no packet value of the Java protocol");

		return kind;
	}

	/**
	 * Tells whether a packet class is costly: its packets take far more to decode than their bytes, and their value
	 * holds nothing their bytes do not, so a release decodes them only while something wants their class
	 * ({@code JavaProtocol.want}). A frame listener alone does not, so the frames of a costly packet carry its bytes
	 * alone unless its class is wanted. Today the chunk column, {@link ClientboundLevelChunkWithLight}, is the one
	 * costly packet.
	 *
	 * @param type the packet class
	 * @return whether its packets are costly
	 */
	public static boolean isCostly(@NotNull Class<? extends ClientboundPacket> type) {
		return COSTLY.contains(type);
	}

	private static Map<String, Class<? extends ClientboundPacket>> index(List<Class<? extends ClientboundPacket>> types) {
		Map<String, Class<? extends ClientboundPacket>> kinds = new LinkedHashMap<>();
		for (Class<? extends ClientboundPacket> type : types)
			if (kinds.put(type.getSimpleName(), type) != null)
				throw new IllegalStateException("Two packet classes share the kind " + type.getSimpleName());

		return Collections.unmodifiableMap(kinds);
	}
}
