package me.whereareiam.anvil.example.patience.plugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Authentication plugin that accepts patience in place of credentials. Please do not take notes.
 */
public final class ProofOfPatiencePlugin extends JavaPlugin implements Listener {
	private static final String CHALLENGE_STARTED = "proof-of-patience:started";
	private static final String TRY_AGAIN = "proof-of-patience:try-again:";
	private final ReconnectChallenge challenge = new ReconnectChallenge();

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		var command = getCommand("auth");
		if (command != null)
			command.setExecutor((sender, ignored, label, arguments) -> authenticate(
					sender instanceof Player player ? player : null
			));
	}

	@EventHandler
	public void onPreLogin(AsyncPlayerPreLoginEvent event) {
		int attempt = challenge.reconnect(event.getName());
		if (attempt == ReconnectChallenge.NOT_STARTED)
			return;

		if (!challenge.provenBy(attempt)) {
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, TRY_AGAIN + attempt);
			return;
		}

		AuthenticatedProfile.apply(event);
	}

	private boolean authenticate(Player player) {
		if (player == null)
			return false;

		challenge.start(player.getName());
		player.kickPlayer(CHALLENGE_STARTED);
		return true;
	}
}
