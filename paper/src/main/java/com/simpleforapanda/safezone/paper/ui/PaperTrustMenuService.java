package com.simpleforapanda.safezone.paper.ui;

import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.BLUE;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

public final class PaperTrustMenuService implements Listener {
	private static final String ADMIN_PERMISSION = "safezone.command.admin";
	private static final String MENU_TITLE = "Safe Zone • Build Access";
	private static final int TOTAL_SLOT_COUNT = 54;
	private static final int PLAYER_GRID_SLOT_COUNT = 45;
	private static final int PREVIOUS_PAGE_SLOT = 45;
	private static final int PAGE_INFO_SLOT = 49;
	private static final int NEXT_PAGE_SLOT = 53;

	private final PaperClaimStore claimStore;

	public PaperTrustMenuService(PaperClaimStore claimStore) {
		this.claimStore = Objects.requireNonNull(claimStore, "claimStore");
	}

	public void open(Player player, String claimId) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(claimId, "claimId");

		MenuHolder holder = new MenuHolder(claimId);
		if (!refresh(holder, player)) {
			return;
		}
		player.openInventory(holder.getInventory());
	}

	@EventHandler(ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
			return;
		}

		event.setCancelled(true);
		int rawSlot = event.getRawSlot();
		if (rawSlot < 0 || rawSlot >= TOTAL_SLOT_COUNT) {
			return;
		}

		if (rawSlot == PREVIOUS_PAGE_SLOT && holder.page() > 0) {
			holder.setPage(holder.page() - 1);
			refresh(holder, player);
			return;
		}
		if (rawSlot == NEXT_PAGE_SLOT && holder.page() + 1 < holder.pageCount()) {
			holder.setPage(holder.page() + 1);
			refresh(holder, player);
			return;
		}
		if (rawSlot >= PLAYER_GRID_SLOT_COUNT) {
			return;
		}

		int entryIndex = holder.page() * PLAYER_GRID_SLOT_COUNT + rawSlot;
		if (entryIndex >= holder.entries().size()) {
			return;
		}

		TrustEntry entry = holder.entries().get(entryIndex);
		try {
			boolean nowTrusted = this.claimStore.toggleTrustedPlayer(holder.claimId(), entry.playerId(), entry.playerName());
			player.sendMessage(text(
				entry.playerName() + (nowTrusted ? " can build in " : " can no longer build in ") + holder.claimId() + ".",
				nowTrusted ? GREEN : YELLOW));
		} catch (IllegalArgumentException exception) {
			player.sendMessage(text("That claim is no longer available.", RED));
			player.closeInventory();
			return;
		}

		refresh(holder, player);
	}

	@EventHandler(ignoreCancelled = true)
	public void onInventoryDrag(InventoryDragEvent event) {
		if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
			event.setCancelled(true);
		}
	}

	private boolean refresh(MenuHolder holder, Player player) {
		Optional<ClaimData> claim = this.claimStore.getClaim(holder.claimId());
		if (claim.isEmpty()) {
			player.sendMessage(text("That claim is no longer available.", RED));
			player.closeInventory();
			return false;
		}
		if (!canManageTrust(player, claim.get())) {
			player.sendMessage(text("Only the owner or an admin can change build access here.", RED));
			player.closeInventory();
			return false;
		}

		List<TrustEntry> entries = collectEntries(claim.get());
		holder.setEntries(entries);
		holder.setPage(Math.max(0, Math.min(holder.page(), Math.max(0, holder.pageCount() - 1))));

		Inventory inventory = holder.getInventory();
		inventory.clear();
		int startIndex = holder.page() * PLAYER_GRID_SLOT_COUNT;
		for (int slot = 0; slot < PLAYER_GRID_SLOT_COUNT; slot++) {
			int entryIndex = startIndex + slot;
			if (entryIndex >= entries.size()) {
				continue;
			}
			inventory.setItem(slot, buildPlayerHead(entries.get(entryIndex)));
		}

		if (holder.page() > 0) {
			inventory.setItem(PREVIOUS_PAGE_SLOT, createNavigationItem(Material.ARROW, "Previous Page", "Go to the previous page"));
		}
		inventory.setItem(PAGE_INFO_SLOT, createPageInfoItem(holder, claim.get()));
		if (holder.page() + 1 < holder.pageCount()) {
			inventory.setItem(NEXT_PAGE_SLOT, createNavigationItem(Material.ARROW, "Next Page", "Go to the next page"));
		}
		return true;
	}

	private List<TrustEntry> collectEntries(ClaimData claim) {
		claim.ensureDefaults();
		Map<UUID, TrustEntry> entriesById = new LinkedHashMap<>();
		for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
			UUID playerId = offlinePlayer.getUniqueId();
			if (claim.ownerUuid.equals(playerId.toString())) {
				continue;
			}

			String playerName = resolvePlayerName(offlinePlayer, claim);
			boolean trusted = claim.isTrusted(playerId);
			if (!trusted && (playerName == null || playerName.isBlank())) {
				continue;
			}

			entriesById.put(playerId, new TrustEntry(
				playerId,
				playerName == null || playerName.isBlank() ? playerId.toString() : playerName,
				trusted,
				offlinePlayer.isOnline()));
		}

		for (String trustedPlayerId : claim.trusted) {
			UUID playerId;
			try {
				playerId = UUID.fromString(trustedPlayerId);
			} catch (IllegalArgumentException exception) {
				continue;
			}
			if (claim.ownerUuid.equals(trustedPlayerId) || entriesById.containsKey(playerId)) {
				continue;
			}

			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
			String playerName = resolvePlayerName(offlinePlayer, claim);
			entriesById.put(playerId, new TrustEntry(
				playerId,
				playerName == null || playerName.isBlank() ? trustedPlayerId : playerName,
				true,
				offlinePlayer.isOnline()));
		}

		return entriesById.values().stream()
			.sorted(Comparator.comparing(TrustEntry::trusted, Comparator.reverseOrder())
				.thenComparing(TrustEntry::online, Comparator.reverseOrder())
				.thenComparing(TrustEntry::playerName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private static String resolvePlayerName(OfflinePlayer offlinePlayer, ClaimData claim) {
		String trustedName = claim.getTrustedName(offlinePlayer.getUniqueId().toString());
		if (trustedName != null && !trustedName.isBlank()) {
			return trustedName;
		}
		return offlinePlayer.getName();
	}

	private static boolean canManageTrust(Player player, ClaimData claim) {
		return claim.owns(player.getUniqueId()) || player.hasPermission(ADMIN_PERMISSION);
	}

	private static ItemStack createNavigationItem(Material material, String title, String description) {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(text(title, YELLOW));
		meta.lore(List.of(text(description, GRAY)));
		stack.setItemMeta(meta);
		return stack;
	}

	private static ItemStack createPageInfoItem(MenuHolder holder, ClaimData claim) {
		long trustedCount = holder.entries().stream().filter(TrustEntry::trusted).count();
		long offlineTrustedCount = holder.entries().stream().filter(entry -> entry.trusted() && !entry.online()).count();
		ItemStack stack = new ItemStack(Material.BOOK);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(text("Build Access", GOLD));
		meta.lore(List.of(
			text("Claim: " + claim.claimId, YELLOW),
			text("Click a head to allow or remove building.", GRAY),
			text("Page " + (holder.page() + 1) + " / " + holder.pageCount(), BLUE),
			text(holder.entries().isEmpty() ? "No players to show right now." : holder.entries().size() + " players shown", GRAY),
			text(trustedCount + " trusted, " + offlineTrustedCount + " offline", DARK_GRAY)));
		stack.setItemMeta(meta);
		return stack;
	}

	private static ItemStack buildPlayerHead(TrustEntry entry) {
		ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) stack.getItemMeta();
		meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.playerId()));
		meta.displayName(text(entry.playerName(), entry.trusted() ? GREEN : GRAY));
		meta.lore(List.of(
			text(entry.trusted() ? "Status: can build here" : "Status: cannot build here", entry.trusted() ? GREEN : GRAY),
			text(entry.trusted() ? "Click: remove build access" : "Click: let them build here", YELLOW),
			text(entry.online() ? "Seen: online now" : "Seen: offline player", AQUA)));
		if (entry.trusted()) {
			meta.addEnchant(Enchantment.UNBREAKING, 1, true);
			meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		}
		stack.setItemMeta(meta);
		return stack;
	}

	private static final class MenuHolder implements InventoryHolder {
		private final String claimId;
		private final Inventory inventory = Bukkit.createInventory(this, TOTAL_SLOT_COUNT, text(MENU_TITLE));
		private List<TrustEntry> entries = List.of();
		private int page;

		private MenuHolder(String claimId) {
			this.claimId = claimId;
		}

		@Override
		public Inventory getInventory() {
			return this.inventory;
		}

		private String claimId() {
			return this.claimId;
		}

		private List<TrustEntry> entries() {
			return this.entries;
		}

		private void setEntries(List<TrustEntry> entries) {
			this.entries = new ArrayList<>(entries);
		}

		private int page() {
			return this.page;
		}

		private void setPage(int page) {
			this.page = page;
		}

		private int pageCount() {
			return Math.max(1, (this.entries.size() + PLAYER_GRID_SLOT_COUNT - 1) / PLAYER_GRID_SLOT_COUNT);
		}
	}

	private record TrustEntry(UUID playerId, String playerName, boolean trusted, boolean online) {
	}
}
