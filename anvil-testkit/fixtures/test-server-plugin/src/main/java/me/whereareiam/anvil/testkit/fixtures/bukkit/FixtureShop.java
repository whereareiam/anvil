package me.whereareiam.anvil.testkit.fixtures.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;

/**
 * A two-screen shop menu as plugins build them from chests: the shop offers a named emerald to buy and one to sell
 * between unnamed glass panes, and buying asks in a second chest to confirm, whose Yes closes the menu and reports
 * {@code anvil:shop:bought}. Clicks in the menu move no items.
 */
final class FixtureShop {
	static final String SHOP_TITLE = "Anvil Shop";
	static final String CONFIRM_TITLE = "Anvil Confirm";
	private static final int BUY = 11;
	private static final int SELL = 15;
	private static final int YES = 2;
	private static final int NO = 6;

	private final Plugin plugin;

	FixtureShop(Plugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Opens the shop.
	 *
	 * @param player the player
	 */
	void open(Player player) {
		Menu menu = new Menu(false);
		Inventory shop = Bukkit.createInventory(menu, 27, SHOP_TITLE);
		menu.inventory = shop;
		for (int slot = 0; slot < shop.getSize(); slot++)
			shop.setItem(slot, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
		shop.setItem(BUY, named(Material.EMERALD, "§aBuy", "§7Costs 64 coins", "§eClick to buy"));
		shop.setItem(SELL, named(Material.EMERALD, "§cSell", "§7Pays 32 coins"));
		player.openInventory(shop);
	}

	/**
	 * Answers a click into one of the menu's screens.
	 *
	 * @param event the click
	 * @return whether the click was one into the menu
	 */
	boolean click(InventoryClickEvent event) {
		InventoryHolder holder = event.getInventory().getHolder();
		if (!(holder instanceof Menu) || !(event.getWhoClicked() instanceof Player))
			return false;

		Menu menu = (Menu) holder;
		Player player = (Player) event.getWhoClicked();
		event.setCancelled(true);
		int slot = event.getRawSlot();
		if (!menu.confirm && slot == BUY)
			Bukkit.getScheduler().runTask(plugin, () -> confirm(player));
		if (menu.confirm && slot == YES)
			Bukkit.getScheduler().runTask(plugin, () -> {
				player.closeInventory();
				player.sendMessage("anvil:shop:bought");
			});
		if (menu.confirm && slot == NO)
			Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
		return true;
	}

	private void confirm(Player player) {
		Menu menu = new Menu(true);
		Inventory confirm = Bukkit.createInventory(menu, 9, CONFIRM_TITLE);
		menu.inventory = confirm;
		confirm.setItem(YES, named(Material.LIME_WOOL, "§aYes"));
		confirm.setItem(NO, named(Material.RED_WOOL, "§cNo"));
		player.openInventory(confirm);
	}

	private static ItemStack named(Material material, String name, String... lore) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(name);
		if (lore.length > 0) meta.setLore(Arrays.asList(lore));
		item.setItemMeta(meta);
		return item;
	}

	/**
	 * Marks the menu's chests, so a click is known to be one into the menu.
	 */
	private static final class Menu implements InventoryHolder {
		private final boolean confirm;
		private Inventory inventory;

		private Menu(boolean confirm) {
			this.confirm = confirm;
		}

		@Override
		public Inventory getInventory() {
			return inventory;
		}
	}
}
