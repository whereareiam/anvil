package me.whereareiam.anvil.protocol.cloudburst.client.v1_26_60;

import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.cloudburst.client.ClientIdentity;
import me.whereareiam.anvil.protocol.cloudburst.client.ClientListener;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientToServerHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetMovementAuthorityPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.jose4j.json.JsonUtil;
import org.jose4j.jws.JsonWebSignature;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Performs the login of one session and keeps it in game: requests the network settings, logs in with the
 * identity, completes the encryption handshake, accepts the server's resource packs without downloading any,
 * registers the item and block definitions the codec needs, requests its chunk radius and announces that the player
 * is initialized once the server spawned it. Reports the login, server-set views and disconnects to the listener,
 * and hands every packet to the {@link ConnectionSession}'s subscriptions before handling it.
 *
 * <p>The granted profile is the player list entry of the player's own entity; when the server lists none shortly
 * after the spawn, it is the profile the identity claimed.</p>
 */
final class ClientPacketHandler implements BedrockPacketHandler {
	/**
	 * Codecs from this protocol on receive item definitions in their own packet rather than in the game start.
	 */
	private static final int ITEM_REGISTRY_PROTOCOL = 776;
	/**
	 * Codecs from this protocol on send a multiplayer token instead of the certificate chain of an Xbox login.
	 */
	private static final int TOKEN_LOGIN_PROTOCOL = 818;
	/**
	 * Codecs from this protocol on know only server authoritative movement, so their game start no longer names it.
	 */
	private static final int SERVER_MOVEMENT_PROTOCOL = 818;
	private static final int CHUNK_RADIUS = 4;
	private static final long LIST_ENTRY_GRACE_MILLIS = 2000;
	private static final DefinitionRegistry<BlockDefinition> UNKNOWN_BLOCKS = new UnknownBlocks();

	private final BedrockClientSession session;
	private final ConnectionSession connection;
	private final BedrockPeer peer;
	private final BedrockCodec codec;
	private final ClientLogin login;
	private final ClientListener<? super ConnectionSession> listener;
	private final Map<Long, ClientProfile> listed = new ConcurrentHashMap<>();
	private final AtomicBoolean reported = new AtomicBoolean();

	private volatile long runtimeEntityId;
	private volatile long uniqueEntityId;
	private volatile boolean spawned;

	ClientPacketHandler(
			BedrockClientSession session,
			ConnectionSession connection,
			BedrockCodec codec,
			ClientLogin login,
			ClientListener<? super ConnectionSession> listener
	) {
		this.session = session;
		this.connection = connection;
		this.peer = session.getPeer();
		this.codec = codec;
		this.login = login;
		this.listener = listener;
	}

	/**
	 * Hands every packet to the connection's subscriptions before handling it.
	 */
	@Override
	public PacketSignal handlePacket(BedrockPacket packet) {
		connection.dispatch(packet);
		return BedrockPacketHandler.super.handlePacket(packet);
	}

	@Override
	public PacketSignal handle(NetworkSettingsPacket packet) {
		session.setCompression(packet.getCompressionThreshold() > 0 ? packet.getCompressionAlgorithm() : PacketCompressionAlgorithm.NONE);
		session.sendPacketImmediately(loginPacket());
		return PacketSignal.HANDLED;
	}

	@Override
	public PacketSignal handle(ServerToClientHandshakePacket packet) {
		try {
			JsonWebSignature handshake = new JsonWebSignature();
			handshake.setCompactSerialization(packet.getJwt());
			PublicKey serverKey = EncryptionUtils.parseKey(handshake.getHeader("x5u"));
			Object salt = JsonUtil.parseJson(handshake.getUnverifiedPayload()).get("salt");
			session.enableEncryption(EncryptionUtils.getSecretKey(login.getIdentity().keyPair().getPrivate(), serverKey, decode(String.valueOf(salt))));
		} catch (Exception exception) {
			fail(DisconnectCause.SERVER, "The server's encryption handshake is invalid: " + exception.getMessage());
			return PacketSignal.HANDLED;
		}

		session.sendPacketImmediately(new ClientToServerHandshakePacket());
		return PacketSignal.HANDLED;
	}

	@Override
	public PacketSignal handle(PlayStatusPacket packet) {
		switch (packet.getStatus()) {
			case LOGIN_SUCCESS -> {
				ClientCacheStatusPacket cache = new ClientCacheStatusPacket();
				cache.setSupported(false);
				session.sendPacket(cache);
			}
			case PLAYER_SPAWN -> initialize();
			default -> fail(DisconnectCause.SERVER, "The server refused the login: " + packet.getStatus());
		}
		return PacketSignal.HANDLED;
	}

	@Override
	public PacketSignal handle(ResourcePacksInfoPacket packet) {
		respond(ResourcePackClientResponsePacket.Status.HAVE_ALL_PACKS);
		return PacketSignal.HANDLED;
	}

	@Override
	public PacketSignal handle(ResourcePackStackPacket packet) {
		respond(ResourcePackClientResponsePacket.Status.COMPLETED);
		return PacketSignal.HANDLED;
	}

	@Override
	public PacketSignal handle(StartGamePacket packet) {
		runtimeEntityId = packet.getRuntimeEntityId();
		uniqueEntityId = packet.getUniqueEntityId();
		if (codec.getProtocolVersion() < ITEM_REGISTRY_PROTOCOL) items(packet.getItemDefinitions());
		peer.getCodecHelper().setBlockDefinitions(UNKNOWN_BLOCKS);
		// The game start rotation is the pitch followed by the yaw.
		connection.started(GameStart.builder()
				.runtimeEntityId(packet.getRuntimeEntityId())
				.uniqueEntityId(packet.getUniqueEntityId())
				.x(packet.getPlayerPosition().getX())
				.y(packet.getPlayerPosition().getY())
				.z(packet.getPlayerPosition().getZ())
				.yaw(packet.getRotation().getY())
				.pitch(packet.getRotation().getX())
				.tick(packet.getCurrentTick())
				.build(), serverMovement(packet.getAuthoritativeMovementMode()));
		listener.teleported(connection, packet.getRotation().getY(), packet.getRotation().getX());

		RequestChunkRadiusPacket radius = new RequestChunkRadiusPacket();
		radius.setRadius(CHUNK_RADIUS);
		radius.setMaxRadius(CHUNK_RADIUS);
		session.sendPacket(radius);
		return PacketSignal.HANDLED;
	}

	@Override
	public PacketSignal handle(SetMovementAuthorityPacket packet) {
		connection.movementAuthority(serverMovement(packet.getMovementMode()));
		return PacketSignal.HANDLED;
	}

	@Override
	public PacketSignal handle(ItemComponentPacket packet) {
		if (codec.getProtocolVersion() >= ITEM_REGISTRY_PROTOCOL) items(packet.getItems());
		return PacketSignal.HANDLED;
	}

	@Override
	@SuppressWarnings("deprecation")
	public PacketSignal handle(PlayerListPacket packet) {
		for (PlayerListPacket.Entry entry : packet.getEntries()) {
			// Codecs before 1.26.40 name the action of the whole packet, later ones that of each entry.
			PlayerListPacket.Action action = entry.getAction() == null ? packet.getAction() : entry.getAction();
			if (action != PlayerListPacket.Action.ADD) continue;

			listed.put(entry.getEntityId(), ClientProfile.builder().name(entry.getName().toString()).uniqueId(entry.getUuid()).build());
		}
		if (spawned && listed.containsKey(uniqueEntityId)) report();
		return PacketSignal.HANDLED;
	}

	@Override
	public PacketSignal handle(MovePlayerPacket packet) {
		if (packet.getRuntimeEntityId() != runtimeEntityId) return PacketSignal.UNHANDLED;

		// A player's rotation is its pitch, yaw and head yaw.
		listener.teleported(connection, packet.getRotation().getY(), packet.getRotation().getX());
		return PacketSignal.HANDLED;
	}

	@Override
	public PacketSignal handle(NetworkStackLatencyPacket packet) {
		if (!packet.isFromServer()) return PacketSignal.UNHANDLED;

		NetworkStackLatencyPacket answer = new NetworkStackLatencyPacket();
		answer.setTimestamp(packet.getTimestamp());
		answer.setFromServer(false);
		session.sendPacket(answer);
		return PacketSignal.HANDLED;
	}

	@Override
	public PacketSignal handle(DisconnectPacket packet) {
		String reason = packet.isMessageSkipped() || packet.getKickMessage() == null ? "disconnected" : packet.getKickMessage().toString();
		DisconnectCause cause = reason.contains("notAuthenticated") ? DisconnectCause.AUTHENTICATION_REQUIRED : DisconnectCause.SERVER;
		listener.disconnected(connection, cause, reason);
		return PacketSignal.HANDLED;
	}

	@Override
	public void onDisconnect(CharSequence reason) {
		connection.close();
		listener.disconnected(connection, DisconnectCause.CONNECTION_LOST, String.valueOf(reason));
	}

	private LoginPacket loginPacket() {
		ClientIdentity identity = login.getIdentity();
		ClientConnection connection = login.getConnection();
		AuthType type = identity.authenticated() ? AuthType.FULL : AuthType.SELF_SIGNED;
		String token = identity.token();
		AuthPayload payload = token != null && codec.getProtocolVersion() >= TOKEN_LOGIN_PROTOCOL
				? new TokenPayload(token, type)
				: new CertificateChainPayload(identity.chain(), type);

		LoginPacket packet = new LoginPacket();
		packet.setProtocolVersion(codec.getProtocolVersion());
		packet.setAuthPayload(payload);
		packet.setClientJwt(identity.clientData(codec.getMinecraftVersion(), connection.getHost() + ":" + connection.getPort()));
		return packet;
	}

	private void initialize() {
		SetLocalPlayerAsInitializedPacket initialized = new SetLocalPlayerAsInitializedPacket();
		initialized.setRuntimeEntityId(runtimeEntityId);
		session.sendPacket(initialized);
		spawned = true;

		if (listed.containsKey(uniqueEntityId)) report();
		else peer.getChannel().eventLoop().schedule(this::report, LIST_ENTRY_GRACE_MILLIS, TimeUnit.MILLISECONDS);
	}

	private void report() {
		if (!reported.compareAndSet(false, true) || !session.isConnected()) return;

		listener.loggedIn(connection, listed.getOrDefault(uniqueEntityId, login.getProfile()));
	}

	private void respond(ResourcePackClientResponsePacket.Status status) {
		ResourcePackClientResponsePacket response = new ResourcePackClientResponsePacket();
		response.setStatus(status);
		session.sendPacketImmediately(response);
	}

	private void items(List<ItemDefinition> definitions) {
		List<ItemDefinition> registered = new ArrayList<>(definitions);
		registered.add(new SimpleItemDefinition("minecraft:empty", 0, false));
		peer.getCodecHelper().setItemDefinitions(SimpleDefinitionRegistry.<ItemDefinition>builder().addAll(registered).build());
		connection.registered(registered);
	}

	/**
	 * Tells whether a movement mode the server named makes it authoritative; codecs that no longer name one know
	 * only server authority.
	 */
	@SuppressWarnings("deprecation")
	private boolean serverMovement(AuthoritativeMovementMode mode) {
		return codec.getProtocolVersion() >= SERVER_MOVEMENT_PROTOCOL || mode != AuthoritativeMovementMode.CLIENT;
	}

	private void fail(DisconnectCause cause, String reason) {
		listener.disconnected(connection, cause, reason);
		session.disconnect(reason);
	}

	/**
	 * Decodes a handshake salt in either Base64 alphabet.
	 */
	private static byte[] decode(String salt) {
		return Base64.getDecoder().decode(salt.replace('-', '+').replace('_', '/'));
	}

	/**
	 * Block definitions of a codec that never needs a block's name: every runtime id stands for itself.
	 */
	private static final class UnknownBlocks implements DefinitionRegistry<BlockDefinition> {
		@Override
		public BlockDefinition getDefinition(int runtimeId) {
			return () -> runtimeId;
		}

		@Override
		public boolean isRegistered(BlockDefinition definition) {
			return true;
		}
	}
}
