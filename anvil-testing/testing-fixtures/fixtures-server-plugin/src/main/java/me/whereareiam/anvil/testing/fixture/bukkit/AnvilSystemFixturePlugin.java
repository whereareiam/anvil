package me.whereareiam.anvil.testing.fixture.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Internal Bukkit fixture exposing observable behavior to Anvil's live system tests.
 */
public final class AnvilSystemFixturePlugin extends JavaPlugin implements Listener {
	private static final String CHANNEL = "BungeeCord";
	private static final String AUTH_STARTED = "anvil:auth:started";
	private static final String AUTH_REJOIN_FORBIDDEN = "anvil:auth:rejoin-forbidden:";
	private final Map<String, AtomicInteger> authenticationAttempts = new ConcurrentHashMap<>();

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
		var command = getCommand("anvil-fixture");
		if (command != null)
			command.setExecutor((sender, ignored, label, arguments) -> execute(sender instanceof Player player ? player : null,
					arguments));
		var auth = getCommand("auth");
		if (auth != null)
			auth.setExecutor((sender, ignored, label, arguments) -> authenticate(
					sender instanceof Player player ? player : null
			));
	}

	@EventHandler
	public void onPreLogin(AsyncPlayerPreLoginEvent event) {
		AtomicInteger attempts = authenticationAttempts.get(event.getName());
		if (attempts == null)
			return;

		int attempt = attempts.incrementAndGet();
		if (attempt <= 2) {
			event.disallow(
					AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
					AUTH_REJOIN_FORBIDDEN + attempt
			);
			return;
		}

		AuthenticatedProfile.apply(event);
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		event.getPlayer().sendMessage("anvil:welcome:" + event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onBlockInteraction(PlayerInteractEvent event) {
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK)
			event.getPlayer().sendMessage("anvil:block:" + event.getClickedBlock().getType().getKey());
		else if (event.getAction() == Action.RIGHT_CLICK_AIR)
			event.getPlayer().sendMessage("anvil:item-use:" + event.getMaterial().getKey());
	}

	@EventHandler
	public void onMove(PlayerMoveEvent event) {
		if (event.getTo() != null && event.getFrom().distanceSquared(event.getTo()) > 0.0001D)
			event.getPlayer().sendMessage("anvil:move");
	}

	@EventHandler
	public void onEntityInteraction(PlayerInteractAtEntityEvent event) {
		event.getPlayer().sendMessage("anvil:entity:" + event.getRightClicked().getEntityId());
	}

	@EventHandler
	public void onEntityInteraction(PlayerInteractEntityEvent event) {
		if (!(event instanceof PlayerInteractAtEntityEvent))
			event.getPlayer().sendMessage("anvil:entity:" + event.getRightClicked().getEntityId());
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (event.getWhoClicked() instanceof Player player)
			player.sendMessage("anvil:click:" + event.getRawSlot());
	}

	private boolean execute(Player player, String[] arguments) {
		if (player == null)
			return false;

		String operation = arguments.length == 0 ? "ping" : arguments[0];
		switch (operation) {
			case "ping" -> player.sendMessage("anvil:pong");
			case "gui" -> openInventory(player);
			case "item" -> {
				player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
				player.getInventory().setHeldItemSlot(0);
				player.sendMessage("anvil:item:diamond");
			}
			case "position" -> sendPosition(player);
			case "block" -> createTargetBlock(player);
			case "entity" -> spawnEntity(player);
			case "kick" -> player.kickPlayer("anvil:requested-kick");
			case "transfer" -> transfer(player, arguments);
			default -> player.sendMessage("anvil:unknown:" + operation);
		}
		return true;
	}

	private boolean authenticate(Player player) {
		if (player == null)
			return false;

		authenticationAttempts.put(player.getName(), new AtomicInteger());
		player.kickPlayer(AUTH_STARTED);
		return true;
	}

	private void openInventory(Player player) {
		Inventory inventory = Bukkit.createInventory(null, 9, "Anvil system fixture");
		inventory.setItem(4, new ItemStack(Material.DIAMOND, 3));
		player.openInventory(inventory);
		player.sendMessage("anvil:gui:open");
	}

	private void spawnEntity(Player player) {
		Location target = player.getEyeLocation().add(0, 0, 1);
		clearEntitySpace(player.getEyeLocation());
		clearEntitySpace(target);
		ArmorStand stand = player.getWorld().spawn(target, ArmorStand.class);
		stand.setGravity(false);
		stand.setInvulnerable(true);
		stand.setCustomName("Anvil target");
		stand.setCustomNameVisible(true);
		getServer().getScheduler().runTaskTimer(this, task -> {
			if (!player.isOnline() || !stand.isValid()) {
				task.cancel();
				return;
			}
			if (!stand.getTrackedPlayers().contains(player) || !player.hasLineOfSight(stand))
				return;
			player.sendMessage("anvil:entity-id:" + stand.getEntityId());
			task.cancel();
		}, 1L, 1L);
	}

	private void clearEntitySpace(Location location) {
		for (int height = 0; height < 3; height++)
			location.clone().add(0, height, 0).getBlock().setType(Material.AIR);
	}

	private void sendPosition(Player player) {
		Location location = player.getLocation();
		player.sendMessage("anvil:position:" + location.getX() + ":" + location.getY() + ":"
				+ location.getZ() + ":" + location.getYaw() + ":" + location.getPitch());
	}

	private void createTargetBlock(Player player) {
		Block block = player.getLocation().getBlock().getRelative(BlockFace.EAST);
		block.setType(Material.DIAMOND_BLOCK);
		player.sendMessage("anvil:block-position:" + block.getX() + ":" + block.getY() + ":" + block.getZ());
	}

	private void transfer(Player player, String[] arguments) {
		if (arguments.length < 2) {
			player.sendMessage("anvil:transfer:missing-server");
			return;
		}

		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			 DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeUTF("Connect");
			output.writeUTF(arguments[1]);
			player.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to create proxy transfer message", exception);
		}
	}
}
