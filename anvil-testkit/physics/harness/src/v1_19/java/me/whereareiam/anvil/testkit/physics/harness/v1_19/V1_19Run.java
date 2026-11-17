package me.whereareiam.anvil.testkit.physics.harness.v1_19;

import me.whereareiam.anvil.testkit.physics.harness.Course;
import me.whereareiam.anvil.testkit.physics.harness.CourseKey;
import me.whereareiam.anvil.testkit.physics.harness.PacketLine;
import me.whereareiam.anvil.testkit.physics.harness.TickSample;
import me.whereareiam.anvil.testkit.physics.harness.VanillaClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A course running on a vanilla client of Minecraft 1.19.4 to 1.21.1, whose tick runs what {@code Minecraft.tick}
 * runs for the player: the game mode's tick, which ticks the connection, then the level's entities, followed by the
 * frame {@code Minecraft.runTick} renders. These releases end no tick with a packet.
 */
final class V1_19Run implements VanillaClient.CourseRun {
	private final HeadlessMinecraft minecraft;
	private final ClientPacketListener listener;
	private final RecordingConnection connection;
	private final Map<CourseKey, KeyMapping> keys;
	private final List<ClientboundLevelChunkWithLightPacket> chunks;

	V1_19Run(HeadlessMinecraft minecraft, RecordingConnection connection, Map<CourseKey, KeyMapping> keys, List<ClientboundLevelChunkWithLightPacket> chunks) {
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
		listener.handleMovePlayer(V1_19Server.position(teleport));
	}

	@Override
	public void tick() {
		minecraft.gameMode.tick();
		minecraft.level.tickEntities();
		minecraft.renderer().frame();
	}

	@Override
	public TickSample sample() {
		LocalPlayer player = minecraft.player;
		Vec3 movement = player.getDeltaMovement();
		return new TickSample(player.getX(), player.getY(), player.getZ(), movement.x, movement.y, movement.z,
			V1_19Release.onGround(player), player.horizontalCollision);
	}

	@Override
	public List<PacketLine> sent() {
		return connection.sent().drain();
	}
}
