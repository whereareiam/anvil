package me.whereareiam.anvil.testkit.physics.harness.v1_21_11;

import me.whereareiam.anvil.testkit.physics.harness.Course;
import me.whereareiam.anvil.testkit.physics.harness.CourseKey;
import me.whereareiam.anvil.testkit.physics.harness.PacketLine;
import me.whereareiam.anvil.testkit.physics.harness.TickSample;
import me.whereareiam.anvil.testkit.physics.harness.VanillaClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A course running on the vanilla client of Minecraft 1.21.11, whose tick runs what {@code Minecraft.tick} runs for
 * the player: the game mode's tick, which ticks the connection and with it the listener's wait for the level, the
 * level's entities, then the end of the client tick, followed by the frame {@code Minecraft.runTick} renders.
 */
final class V1_21_11Run implements VanillaClient.CourseRun {
	private final HeadlessMinecraft minecraft;
	private final ClientPacketListener listener;
	private final RecordingConnection connection;
	private final Map<CourseKey, KeyMapping> keys;
	private final List<ClientboundLevelChunkWithLightPacket> chunks;

	V1_21_11Run(HeadlessMinecraft minecraft, RecordingConnection connection, Map<CourseKey, KeyMapping> keys, List<ClientboundLevelChunkWithLightPacket> chunks) {
		this.minecraft = minecraft;
		this.listener = (ClientPacketListener) connection.packetListener;
		this.connection = connection;
		this.keys = keys;
		this.chunks = chunks;
	}

	@Override
	public void hold(Set<CourseKey> held) {
		keys.forEach((key, mapping) -> mapping.setDown(held.contains(key)));
	}

	@Override
	public void loadChunks() {
		chunks.forEach(listener::handleLevelChunkWithLight);
	}

	@Override
	public void teleport(Course.Teleport teleport) {
		listener.handleMovePlayer(V1_21_11Server.position(teleport));
	}

	@Override
	public void tick() {
		minecraft.gameMode.tick();
		minecraft.level.tickEntities();
		ClientPacketListener current = minecraft.getConnection();
		if (current != null)
			current.send(ServerboundClientTickEndPacket.INSTANCE);
		minecraft.renderer().frame();
	}

	@Override
	public TickSample sample() {
		LocalPlayer player = minecraft.player;
		Vec3 movement = player.getDeltaMovement();
		return new TickSample(player.getX(), player.getY(), player.getZ(), movement.x, movement.y, movement.z, player.onGround(),
			player.horizontalCollision);
	}

	@Override
	public List<PacketLine> sent() {
		return connection.sent().drain();
	}
}
