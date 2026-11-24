package me.whereareiam.anvil.testkit.physics.harness.v26;

import me.whereareiam.anvil.testkit.physics.harness.PacketLine;
import me.whereareiam.anvil.testkit.physics.harness.Unconstructed;
import me.whereareiam.anvil.testkit.physics.harness.VanillaHarness;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.Optional;
import java.util.Set;

/**
 * What the headless vanilla client of Minecraft 26.1.2 sets up and sends differently from the other releases of its
 * family, compiled against that client alone: the level renderer draws the level, the screen is the game's own, and the login names the previous game mode by a nullable game type.
 */
final class V26Client {
	private static final int SEA_LEVEL = 63;

	private V26Client() {
	}

	/**
	 * Allocates what draws the level, the level renderer, which draws nothing, and gives it the game.
	 */
	static HeadlessRendering rendering(HeadlessMinecraft minecraft) {
		HeadlessLevelRenderer rendering = Unconstructed.allocate(HeadlessLevelRenderer.class);
		rendering.debugRenderer = new DebugRenderer();
		rendering.sections = new CompiledSections();
		minecraft.levelRenderer = rendering;
		return rendering;
	}

	/**
	 * Gives the game a GUI that draws nothing, with the overlay and chat the handlers reach.
	 */
	static void gui(HeadlessMinecraft minecraft, DebugScreenOverlay overlay, ChatComponent chat) {
		Gui gui = Unconstructed.allocate(Gui.class);
		gui.debugOverlay = overlay;
		gui.chat = chat;
		minecraft.gui = gui;
	}

	/**
	 * Shows a screen without drawing it: the game keeps it as its screen.
	 */
	static void show(HeadlessMinecraft minecraft, Screen screen) {
		minecraft.screen = screen;
	}

	/**
	 * Creates a fresh level of the overworld that loads {@code radius} chunks around its center, drawn by the headless
	 * client's rendering.
	 */
	static ClientLevel level(ClientPacketListener listener, Holder<DimensionType> overworld, int radius, HeadlessRendering rendering) {
		return new ClientLevel(listener, new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false), Level.OVERWORLD, overworld, radius,
			radius, (HeadlessLevelRenderer) rendering, false, 0L, SEA_LEVEL);
	}

	/**
	 * The login a Paper server sends a fresh survival player of the overworld.
	 */
	static ClientboundLoginPacket login(Holder<DimensionType> overworld, int viewDistance, int maxPlayers) {
		CommonPlayerSpawnInfo spawn = new CommonPlayerSpawnInfo(overworld, Level.OVERWORLD, 0L, GameType.SURVIVAL, null, false, false,
			Optional.empty(), 0, SEA_LEVEL);
		return new ClientboundLoginPacket(VanillaHarness.ENTITY_ID, false, Set.of(Level.OVERWORLD), maxPlayers, viewDistance, viewDistance,
			false, true, false, spawn, false);
	}

	/**
	 * The telemetry of a world session that sends nothing, named neither by a world nor by a session.
	 */
	static WorldSessionTelemetryManager telemetry() {
		return new WorldSessionTelemetryManager(TelemetryEventSender.DISABLED, false, null, null);
	}

	/**
	 * The trace line of a swing the client answers the server's animation of its player with, or nothing for another
	 * packet.
	 */
	static Optional<PacketLine> swing(Packet<?> packet) {
		if (!(packet instanceof ServerboundSwingPacket swing))
			return Optional.empty();
		return Optional.of(PacketLine.of("ServerboundSwing").with("hand", swing.getHand()));
	}

	/**
	 * The trace line of a teleport's acceptance, which carries the teleport's ID.
	 */
	static PacketLine accept(ServerboundAcceptTeleportationPacket accept) {
		return PacketLine.of("ServerboundAcceptTeleportation").with("id", accept.getId());
	}
}
