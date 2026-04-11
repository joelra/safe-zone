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
import java.util.HashMap;
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
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

public final class PaperTrustMenuService implements Listener {
	private static final String ADMIN_PERMISSION = "safezone.command.admin";
	private static final String MENU_TITLE = "Safe Zone • Build Access";
	private static final int TOTAL_SLOT_COUNT = 54;
	private static final int NAV_ROW_START = 45;
	private static final int PREVIOUS_PAGE_SLOT = 45;
	private static final int PAGE_INFO_SLOT = 49;
	private static final int NEXT_PAGE_SLOT = 53;
	// Trusted section is always in rows 0 (header) + rows 1..MAX_TRUSTED_ROWS (heads).
	// Two rows of trusted heads gives 18 slots, which is generous for most servers.
	// If more players are trusted than fit, the header shows "+N more".
	private static final int MAX_TRUSTED_ROWS = 2;

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

		if (rawSlot == PREVIOUS_PAGE_SLOT && holder.untrustedPage() > 0) {
			holder.setUntrustedPage(holder.untrustedPage() - 1);
			refresh(holder, player);
			return;
		}
		if (rawSlot == NEXT_PAGE_SLOT && holder.untrustedPage() + 1 < holder.untrustedPageCount()) {
			holder.setUntrustedPage(holder.untrustedPage() + 1);
			refresh(holder, player);
			return;
		}

		TrustEntry entry = holder.slotToEntry().get(rawSlot);
		if (entry == null) {
			return;
		}

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

		List<TrustEntry> allEntries = collectEntries(claim.get());
		List<TrustEntry> trusted = allEntries.stream().filter(TrustEntry::trusted).toList();
		List<TrustEntry> untrusted = allEntries.stream().filter(e -> !e.trusted()).toList();

		// --- Layout ---
		// Row 0:                  trusted section header (lime glass panes)
		// Rows 1..trustedRows:    trusted player heads
		// Row trustedRows+1:      untrusted section header (gray glass panes)
		// Rows trustedRows+2..4:  untrusted player heads
		// Row 5 (slots 45-53):    navigation
		int trustedRows = Math.min(MAX_TRUSTED_ROWS, Math.max(1, (trusted.size() + 8) / 9));
		int trustedContentStart = 9;
		int trustedContentEnd = trustedContentStart + trustedRows * 9;
		int untrustedHeaderStart = trustedContentEnd;
		int untrustedContentStart = untrustedHeaderStart + 9;
		int untrustedAvailableSlots = NAV_ROW_START - untrustedContentStart;

		int visibleTrustedCount = Math.min(trusted.size(), trustedRows * 9);
		boolean trustedOverflow = trusted.size() > visibleTrustedCount;

		int untrustedPageCount = Math.max(1, (untrusted.size() + untrustedAvailableSlots - 1) / untrustedAvailableSlots);
		holder.setUntrustedPageCount(untrustedPageCount);
		holder.setUntrustedPage(Math.max(0, Math.min(holder.untrustedPage(), untrustedPageCount - 1)));

		// --- Build slot→entry map ---
		Map<Integer, TrustEntry> slotToEntry = new HashMap<>();
		for (int i = 0; i < visibleTrustedCount; i++) {
			slotToEntry.put(trustedContentStart + i, trusted.get(i));
		}
		int untrustedOffset = holder.untrustedPage() * untrustedAvailableSlots;
		for (int i = 0; i < untrustedAvailableSlots; i++) {
			int idx = untrustedOffset + i;
			if (idx >= untrusted.size()) {
				break;
			}
			slotToEntry.put(untrustedContentStart + i, untrusted.get(idx));
		}
		holder.setSlotToEntry(slotToEntry);

		// --- Render ---
		Inventory inventory = holder.getInventory();
		inventory.clear();

		// Trusted section header
		String trustedLabel = trustedOverflow
			? "Trusted Players (+" + (trusted.size() - visibleTrustedCount) + " more)"
			: "Trusted Players";
		renderSectionHeader(inventory, 0, Material.LIME_STAINED_GLASS_PANE, GREEN, trustedLabel);

		// Trusted player heads
		for (var e : slotToEntry.entrySet()) {
			int slot = e.getKey();
			if (slot >= trustedContentStart && slot < trustedContentEnd) {
				inventory.setItem(slot, buildPlayerHead(e.getValue()));
			}
		}

		// Untrusted section header
		String untrustedLabel = untrusted.isEmpty() ? "Other Players" : "Other Players (" + untrusted.size() + ")";
		renderSectionHeader(inventory, untrustedHeaderStart, Material.GRAY_STAINED_GLASS_PANE, GRAY, untrustedLabel);

		// Untrusted player heads
		for (var e : slotToEntry.entrySet()) {
			int slot = e.getKey();
			if (slot >= untrustedContentStart) {
				inventory.setItem(slot, buildPlayerHead(e.getValue()));
			}
		}

		// Navigation row
		inventory.setItem(PAGE_INFO_SLOT, createPageInfoItem(claim.get(), trusted.size(), untrusted.size(),
			holder.untrustedPage(), untrustedPageCount));
		if (holder.untrustedPage() > 0) {
			inventory.setItem(PREVIOUS_PAGE_SLOT, createNavigationItem(Material.ARROW, "Previous Page", "Go to the previous page"));
		}
		if (holder.untrustedPage() + 1 < untrustedPageCount) {
			inventory.setItem(NEXT_PAGE_SLOT, createNavigationItem(Material.ARROW, "Next Page", "Go to the next page"));
		}

		return true;
	}

	private static void renderSectionHeader(Inventory inventory, int startSlot, Material pane,
			net.kyori.adventure.text.format.NamedTextColor labelColor, String label) {
		for (int col = 0; col < 9; col++) {
			int slot = startSlot + col;
			ItemStack stack = new ItemStack(pane);
			ItemMeta meta = stack.getItemMeta();
			if (col == 4) {
				meta.displayName(text(label, labelColor));
			} else {
				meta.displayName(text(" ", WHITE));
			}
			stack.setItemMeta(meta);
			inventory.setItem(slot, stack);
		}
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

	private static ItemStack createPageInfoItem(ClaimData claim, int trustedCount, int untrustedCount,
			int untrustedPage, int untrustedPageCount) {
		ItemStack stack = new ItemStack(Material.BOOK);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(text("Build Access", GOLD));
		List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
		lore.add(text("Claim: " + claim.claimId, YELLOW));
		lore.add(text("Click a head to allow or remove building.", GRAY));
		lore.add(text(trustedCount + " trusted, " + untrustedCount + " other players", DARK_GRAY));
		if (untrustedPageCount > 1) {
			lore.add(text("Other players: page " + (untrustedPage + 1) + " / " + untrustedPageCount, BLUE));
		}
		meta.lore(lore);
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
		private Map<Integer, TrustEntry> slotToEntry = Map.of();
		private int untrustedPage;
		private int untrustedPageCount = 1;

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

		private Map<Integer, TrustEntry> slotToEntry() {
			return this.slotToEntry;
		}

		private void setSlotToEntry(Map<Integer, TrustEntry> map) {
			this.slotToEntry = Map.copyOf(map);
		}

		private int untrustedPage() {
			return this.untrustedPage;
		}

		private void setUntrustedPage(int page) {
			this.untrustedPage = page;
		}

		private int untrustedPageCount() {
			return this.untrustedPageCount;
		}

		private void setUntrustedPageCount(int count) {
			this.untrustedPageCount = count;
		}
	}

	private record TrustEntry(UUID playerId, String playerName, boolean trusted, boolean online) {
	}
}
