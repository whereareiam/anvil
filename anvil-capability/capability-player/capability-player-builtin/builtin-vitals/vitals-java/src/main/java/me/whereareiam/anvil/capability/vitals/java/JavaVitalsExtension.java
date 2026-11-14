package me.whereareiam.anvil.capability.vitals.java;

import me.whereareiam.anvil.capability.vitals.channel.VitalsChannel;
import me.whereareiam.anvil.capability.vitals.model.Death;
import me.whereareiam.anvil.capability.vitals.model.VitalStatus;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.protocol.java.api.packet.health.ServerboundClientCommand;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.type.ClientCommandAction;
import me.whereareiam.anvil.protocol.java.api.type.GameEventType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Installs the built-in vitals capability for players that speak the Java Edition protocol: it emits the health, food
 * and saturation the server sends, the initial values when a connection starts, and each death the server reports for
 * the player's own entity, and respawns the player on request.
 *
 * <p>Like the game's client, it respawns the player by itself when it dies while the server has the death screen
 * turned off: the login says whether the death screen shows, and the immediate-respawn game event changes it.</p>
 */
public final class JavaVitalsExtension implements WorkerExtension<Object> {
	private static final ServerboundClientCommand RESPAWN =
			new ServerboundClientCommand(ClientCommandAction.PERFORM_RESPAWN);

	@Override
	public @NotNull String id() {
		return VitalsChannel.ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		Life life = new Life();
		operations.register(VitalsChannel.RESPAWN, ignored -> {
			protocol.send(RESPAWN);
			return null;
		});

		Subscription binding = Subscription.all(
				protocol.listen(ClientboundLogin.class,
						login -> life.joined(login.getEntityId(), login.isShowDeathScreen())),
				protocol.listen(ClientboundGameEvent.class, event -> {
					if (event.getEvent() == GameEventType.IMMEDIATE_RESPAWN) life.deathScreen(event.getValue() == 0F);
				}),
				protocol.listen(ClientboundSetHealth.class, health -> player.emit(VitalsChannel.STATUS,
						new VitalStatus(health.getHealth(), health.getFood(), health.getSaturation()))),
				protocol.listen(ClientboundPlayerCombatKill.class, kill -> {
					if (!life.isPlayer(kill.getPlayerId())) return;

					boolean immediately = !life.showsDeathScreen();
					player.emit(VitalsChannel.DIED, new Death(kill.getMessage(), immediately));
					if (immediately) protocol.send(RESPAWN);
				}),
				player.bindConnection(() -> {
					life.reset();
					player.emit(VitalsChannel.STATUS, VitalStatus.INITIAL);
					return () -> { };
				})
		);

		return binding::close;
	}

	/**
	 * What the client knows of the current connection's player: its entity, once it joined a level, and whether it
	 * shows the death screen. The connection's packets arrive on its packet thread, while a new connection starts
	 * from another thread.
	 */
	private static final class Life {
		private @Nullable Integer entityId;
		private boolean deathScreen = true;

		private synchronized void reset() {
			entityId = null;
			deathScreen = true;
		}

		private synchronized void joined(int entityId, boolean deathScreen) {
			this.entityId = entityId;
			this.deathScreen = deathScreen;
		}

		private synchronized void deathScreen(boolean deathScreen) {
			this.deathScreen = deathScreen;
		}

		private synchronized boolean isPlayer(int entityId) {
			return this.entityId != null && this.entityId == entityId;
		}

		private synchronized boolean showsDeathScreen() {
			return deathScreen;
		}
	}
}
