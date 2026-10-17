package me.whereareiam.anvil.testkit.fixtures.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
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
import java.util.Arrays;
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
	private FixtureCookies cookies;
	private FixtureDisplays displays;

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
		var command = getCommand("anvil-fixture");
		if (command != null)
			command.setExecutor((sender, ignored, label, arguments) -> execute(player(sender), arguments));
		cookies = FixtureCookies.load(getClassLoader());
		var cookie = getCommand("anvil-cookie");
		if (cookie != null)
			cookie.setExecutor((sender, ignored, label, arguments) -> cookie(arguments));
		displays = FixtureDisplays.load(getClassLoader());
		var entity = getCommand("anvil-entity");
		if (entity != null)
			entity.setExecutor((sender, ignored, label, arguments) -> entity(arguments));
		var auth = getCommand("auth");
		if (auth != null)
			auth.setExecutor((sender, ignored, label, arguments) -> authenticate(player(sender)));
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
		if (event.getFrom().distanceSquared(event.getTo()) > 0.0001D)
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
		if (event.getWhoClicked() instanceof Player)
			event.getWhoClicked().sendMessage("anvil:click:" + event.getRawSlot());
	}

	private boolean execute(Player player, String[] arguments) {
		if (player == null)
			return false;

		String operation = arguments.length == 0 ? "ping" : arguments[0];
		switch (operation) {
			case "ping":
				player.sendMessage("anvil:pong");
				break;
			case "gui":
				openInventory(player);
				break;
			case "item":
				player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
				player.getInventory().setHeldItemSlot(0);
				player.sendMessage("anvil:item:diamond");
				break;
			case "position":
				sendPosition(player);
				break;
			case "block":
				createTargetBlock(player);
				break;
			case "entity":
				spawnEntity(player);
				break;
			case "kick":
				player.kickPlayer("anvil:requested-kick");
				break;
			case "transfer":
				transfer(player, arguments);
				break;
			case "dialog":
				showDialog(player);
				break;
			case "dialog-echo":
				player.sendMessage("anvil:dialog-echo:" + String.join(":", Arrays.copyOfRange(arguments, 1, arguments.length)));
				break;
			case "op":
				grantOperator(player);
				break;
			case "dialog-clear":
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dialog clear " + player.getName());
				break;
			default:
				player.sendMessage("anvil:unknown:" + operation);
		}
		return true;
	}

	/**
	 * Stores or requests a cookie of a player named by the console, so a test drives cookies without sending the
	 * player's own commands, which count towards the server's spam limit.
	 */
	private boolean cookie(String[] arguments) {
		Player player = arguments.length < 3 ? null : Bukkit.getPlayerExact(arguments[0]);
		if (player == null)
			return false;
		if (cookies == null) {
			player.sendMessage("anvil:cookie:unsupported");
			return true;
		}

		if (arguments[1].equals("store") && arguments.length == 4)
			cookies.store(player, arguments[2], arguments[3]);
		else if (arguments[1].equals("request"))
			cookies.request(player, arguments[2]);
		else
			return false;
		return true;
	}

	/**
	 * Shows text above a player named by the console, as a chat bubble plugin does, or removes it again, each
	 * confirmed to the player as chat: an invisible armor stand with a visible custom name riding the player
	 * ({@code anvil:bubble:<id>}), a text display above the player ({@code anvil:display:<id>}, or
	 * {@code anvil:display:unsupported} before Minecraft 1.19.4), a glowing stack of three diamonds dropped next to
	 * the player that nobody can pick up ({@code anvil:dropped:<id>}), and the removal of an entity
	 * ({@code anvil:removed:<id>}).
	 */
	private boolean entity(String[] arguments) {
		Player player = arguments.length < 2 ? null : Bukkit.getPlayerExact(arguments[0]);
		if (player == null)
			return false;
		if (arguments.length < 3 && !arguments[1].equals("drop"))
			return false;

		String text = String.join(" ", Arrays.copyOfRange(arguments, 2, arguments.length));
		switch (arguments[1]) {
			case "bubble":
				ArmorStand bubble = player.getWorld().spawn(player.getLocation().add(0, 2, 0), ArmorStand.class);
				bubble.setVisible(false);
				bubble.setGravity(false);
				bubble.setCustomName(text);
				bubble.setCustomNameVisible(true);
				player.addPassenger(bubble);
				player.sendMessage("anvil:bubble:" + bubble.getEntityId());
				return true;
			case "display":
				if (displays == null)
					player.sendMessage("anvil:display:unsupported");
				else
					player.sendMessage("anvil:display:" + displays.show(player.getLocation().add(0, 2.5, 0), text));
				return true;
			case "drop":
				Item dropped = player.getWorld().dropItem(player.getLocation().add(1, 0, 1), new ItemStack(Material.DIAMOND, 3));
				dropped.setPickupDelay(Integer.MAX_VALUE);
				dropped.setGlowing(true);
				player.sendMessage("anvil:dropped:" + dropped.getEntityId());
				return true;
			case "remove":
				int entityId = Integer.parseInt(arguments[2]);
				player.getWorld().getEntities().stream()
						.filter(candidate -> candidate.getEntityId() == entityId)
						.forEach(Entity::remove);
				player.sendMessage("anvil:removed:" + entityId);
				return true;
			default:
				return false;
		}
	}

	private static Player player(CommandSender sender) {
		return sender instanceof Player ? (Player) sender : null;
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
		ArmorStand stand = player.getWorld().spawn(player.getLocation().add(1, 0, 0), ArmorStand.class);
		stand.setCustomName("Anvil target");
		stand.setCustomNameVisible(true);
		player.sendMessage("anvil:entity-id:" + stand.getEntityId());
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

	/**
	 * Shows a dialog through the vanilla command, which servers from Minecraft 1.21.6 have. It stays open after a
	 * button: Echo echoes the inputs through a command template, Nested opens another dialog, Unknown, Signed and Seed
	 * run commands the client asks about first, and Clear asks the server to clear the dialog.
	 */
	private void showDialog(Player player) {
		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dialog show " + player.getName() + " {type:\"minecraft:multi_action\","
				+ "title:\"anvil:dialog\",body:{type:\"minecraft:plain_message\",contents:\"anvil:dialog-body\"},"
				+ "inputs:[{type:\"minecraft:text\",key:\"word\",label:\"Word\"},"
				+ "{type:\"minecraft:boolean\",key:\"flag\",label:\"Flag\",on_true:\"on\",on_false:\"off\"}],"
				+ "can_close_with_escape:false,pause:false,after_action:\"none\",actions:["
				+ "{label:\"Echo\",action:{type:\"minecraft:dynamic/run_command\",template:\"anvil-fixture dialog-echo $(word) $(flag)\"}},"
				+ "{label:\"Nested\",action:{type:\"minecraft:show_dialog\",dialog:{type:\"minecraft:notice\",title:\"anvil:nested\"}}},"
				+ "{label:\"Unknown\",action:{type:\"minecraft:run_command\",command:\"/anvil-unknown-command\"}},"
				+ "{label:\"Signed\",action:{type:\"minecraft:run_command\",command:\"/msg @s anvil:signed\"}},"
				+ "{label:\"Seed\",action:{type:\"minecraft:run_command\",command:\"/seed\"}},"
				+ "{label:\"Clear\",action:{type:\"minecraft:run_command\",command:\"/anvil-fixture dialog-clear\"}}]}");
	}

	/**
	 * Makes the player an operator, so the server sends it the commands that need permissions, marked as such.
	 */
	private static void grantOperator(Player player) {
		player.setOp(true);
		player.updateCommands();
		player.sendMessage("anvil:op");
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
