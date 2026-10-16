package me.whereareiam.anvil.testkit.fixtures.lumi;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityArmorStand;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.player.PlayerInteractEntityEvent;
import cn.nukkit.plugin.PluginBase;

/**
 * Internal Lumi fixture exposing observable behavior to Anvil's live Bedrock system tests. A command sent as the
 * player sets up the scene and tells the player what it needs to act on; the server's own events tell the player
 * what it did, in messages the test receives through the player's Messages capability.
 *
 * <ul>
 *     <li>{@code /anvil-fixture entity} spawns an armor stand at the player's position and reports its runtime ID as
 *     {@code anvil:entity-id:<id>}.</li>
 *     <li>An entity interaction the server accepts is reported as {@code anvil:entity:<id>}, an attack as
 *     {@code anvil:attack:<id>}.</li>
 * </ul>
 */
public final class AnvilLumiFixturePlugin extends PluginBase implements Listener {
	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
		if (!(sender instanceof Player player)) return false;

		String operation = arguments.length == 0 ? "entity" : arguments[0];
		if (operation.equals("entity")) {
			spawnEntity(player);
			return true;
		}

		player.sendMessage("anvil:unknown:" + operation);
		return true;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityInteraction(PlayerInteractEntityEvent event) {
		event.getPlayer().sendMessage("anvil:entity:" + event.getEntity().getId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onAttack(EntityDamageByEntityEvent event) {
		if (event.getDamager() instanceof Player player)
			player.sendMessage("anvil:attack:" + event.getEntity().getId());
	}

	/**
	 * Spawns the armor stand where the player stands, where the server accepts an attack whichever way the player
	 * looks.
	 */
	private static void spawnEntity(Player player) {
		Entity stand = Entity.createEntity(EntityArmorStand.NETWORK_ID, player);
		if (stand == null) {
			player.sendMessage("anvil:entity-id:unavailable");
			return;
		}

		stand.setNameTag("Anvil target");
		stand.setNameTagVisible(true);
		stand.spawnToAll();
		player.sendMessage("anvil:entity-id:" + stand.getId());
	}
}
