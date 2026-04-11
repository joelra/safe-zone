package com.simpleforapanda.safezone.screen;

import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.manager.ClaimManager;
import com.simpleforapanda.safezone.text.SafeZoneText;
import com.simpleforapanda.safezone.manager.PlayerMessageHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TrustMenu extends AbstractContainerMenu {
	private static final int ROWS = 6;
	private static final int TOTAL_SLOT_COUNT = ROWS * 9;
	private static final int NAV_ROW_START = 45;
	private static final int PREVIOUS_PAGE_SLOT = 45;
	private static final int PAGE_INFO_SLOT = 49;
	private static final int NEXT_PAGE_SLOT = 53;
	// Trusted section: row 0 is the header, rows 1..MAX_TRUSTED_ROWS hold heads.
	// Two rows = 18 trusted slots, generous for most servers.
	// Overflow is shown as "+N more" in the header rather than paginating.
	private static final int MAX_TRUSTED_ROWS = 2;

	private final String claimId;
	private final ServerPlayer owner;
	private final ClaimManager claimManager;
	private final SimpleContainer trustContainer;
	// Maps container slot index → TrustEntry for click routing.
	// Rebuilt on every refresh. Non-clickable slots (headers, nav) are absent.
	private Map<Integer, TrustEntry> slotToEntry = Map.of();
	private int untrustedPage;
	private int untrustedPageCount = 1;

	public TrustMenu(int syncId, Inventory playerInventory, ServerPlayer owner, String claimId, ClaimManager claimManager) {
		super(MenuType.GENERIC_9x6, syncId);
		this.claimId = claimId;
		this.owner = owner;
		this.claimManager = claimManager;
		this.trustContainer = new SimpleContainer(TOTAL_SLOT_COUNT);

		for (int row = 0; row < ROWS; row++) {
			for (int column = 0; column < 9; column++) {
				int slot = column + row * 9;
				this.addSlot(new Slot(this.trustContainer, slot, 8 + column * 18, 18 + row * 18) {
					@Override
					public boolean mayPickup(Player player) {
						return false;
					}

					@Override
					public boolean mayPlace(ItemStack stack) {
						return false;
					}
				});
			}
		}

		addPlayerInventory(playerInventory);
		refreshEntries();
	}

	@Override
	public void clicked(int slotIndex, int button, ClickType clickType, Player player) {
		if (clickType != ClickType.PICKUP) {
			return;
		}
		if (slotIndex < 0) {
			return;
		}

		if (slotIndex == PREVIOUS_PAGE_SLOT && this.untrustedPage > 0) {
			this.untrustedPage--;
			refreshEntries();
			return;
		}
		if (slotIndex == NEXT_PAGE_SLOT && this.untrustedPage + 1 < this.untrustedPageCount) {
			this.untrustedPage++;
			refreshEntries();
			return;
		}

		TrustEntry entry = this.slotToEntry.get(slotIndex);
		if (entry == null) {
			return;
		}

		if (player instanceof ServerPlayer) {
			boolean nowTrusted = this.claimManager.toggleTrustedPlayer(this.claimId, entry.playerId(), entry.playerName());
			PlayerMessageHelper.sendStatus(this.owner, nowTrusted ? "SHARED" : "LOCKED",
				nowTrusted ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
				SafeZoneText.trustMenuToggleStatus(nowTrusted, entry.playerName(), this.claimId));
			refreshEntries();
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	private void refreshEntries() {
		Optional<ClaimData> claim = this.claimManager.getClaim(this.claimId);
		if (claim.isEmpty()) {
			this.owner.closeContainer();
			return;
		}

		ClaimData claimData = claim.get();
		claimData.ensureDefaults();
		UUID claimOwnerId = UUID.fromString(claimData.ownerUuid);
		var server = this.claimManager.getServer();
		if (server == null) {
			this.owner.closeContainer();
			return;
		}

		// Build full entry list
		Map<UUID, TrustEntry> entriesById = new LinkedHashMap<>();
		List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers().stream()
			.filter(p -> !p.getUUID().equals(claimOwnerId))
			.toList();

		for (ServerPlayer p : onlinePlayers) {
			boolean trusted = claimData.isTrusted(p.getUUID());
			entriesById.put(p.getUUID(), new TrustEntry(
				p.getUUID(), p.getName().getString(), trusted, true,
				buildPlayerHead(p.getGameProfile(), p.getName().getString(), trusted, true)));
		}

		for (String trustedPlayerId : claimData.trusted) {
			UUID playerId = UUID.fromString(trustedPlayerId);
			if (playerId.equals(claimOwnerId) || entriesById.containsKey(playerId)) {
				continue;
			}
			String playerName = claimData.getTrustedName(trustedPlayerId);
			if (playerName == null || playerName.isBlank()) {
				playerName = playerId.toString();
			}
			entriesById.put(playerId, new TrustEntry(
				playerId, playerName, true, false,
				buildPlayerHead(new GameProfile(playerId, playerName), playerName, true, false)));
		}

		List<TrustEntry> allEntries = entriesById.values().stream()
			.sorted(Comparator.comparing(TrustEntry::trusted).reversed()
				.thenComparing(TrustEntry::online).reversed()
				.thenComparing(TrustEntry::playerName, String.CASE_INSENSITIVE_ORDER))
			.toList();

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

		this.untrustedPageCount = Math.max(1, (untrusted.size() + untrustedAvailableSlots - 1) / untrustedAvailableSlots);
		this.untrustedPage = Math.max(0, Math.min(this.untrustedPage, this.untrustedPageCount - 1));

		// --- Build slot→entry map ---
		Map<Integer, TrustEntry> slotMap = new HashMap<>();
		for (int i = 0; i < visibleTrustedCount; i++) {
			slotMap.put(trustedContentStart + i, trusted.get(i));
		}
		int untrustedOffset = this.untrustedPage * untrustedAvailableSlots;
		for (int i = 0; i < untrustedAvailableSlots; i++) {
			int idx = untrustedOffset + i;
			if (idx >= untrusted.size()) {
				break;
			}
			slotMap.put(untrustedContentStart + i, untrusted.get(idx));
		}
		this.slotToEntry = Map.copyOf(slotMap);

		// --- Render: clear all content slots ---
		for (int slot = 0; slot < TOTAL_SLOT_COUNT; slot++) {
			this.trustContainer.setItem(slot, ItemStack.EMPTY);
		}

		// Trusted section header (row 0)
		String trustedLabel = trustedOverflow
			? "Trusted Players (+" + (trusted.size() - visibleTrustedCount) + " more)"
			: "Trusted Players";
		renderSectionHeader(0, Items.LIME_STAINED_GLASS_PANE, ChatFormatting.GREEN, trustedLabel);

		// Trusted player heads
		for (var e : this.slotToEntry.entrySet()) {
			int slot = e.getKey();
			if (slot >= trustedContentStart && slot < trustedContentEnd) {
				this.trustContainer.setItem(slot, e.getValue().displayStack().copy());
			}
		}

		// Untrusted section header
		String untrustedLabel = untrusted.isEmpty()
			? "Other Players"
			: "Other Players (" + untrusted.size() + ")";
		renderSectionHeader(untrustedHeaderStart, Items.GRAY_STAINED_GLASS_PANE, ChatFormatting.GRAY, untrustedLabel);

		// Untrusted player heads
		for (var e : this.slotToEntry.entrySet()) {
			int slot = e.getKey();
			if (slot >= untrustedContentStart) {
				this.trustContainer.setItem(slot, e.getValue().displayStack().copy());
			}
		}

		// Navigation row
		this.trustContainer.setItem(PREVIOUS_PAGE_SLOT,
			this.untrustedPage > 0 ? createNavigationItem(Items.ARROW, "Previous Page", "Go to the previous page") : ItemStack.EMPTY);
		this.trustContainer.setItem(PAGE_INFO_SLOT, createPageInfoItem(trusted.size(), untrusted.size()));
		this.trustContainer.setItem(NEXT_PAGE_SLOT,
			this.untrustedPage + 1 < this.untrustedPageCount ? createNavigationItem(Items.ARROW, "Next Page", "Go to the next page") : ItemStack.EMPTY);

		this.broadcastFullState();
	}

	private void renderSectionHeader(int startSlot, net.minecraft.world.item.Item pane,
			ChatFormatting labelColor, String label) {
		for (int col = 0; col < 9; col++) {
			int slot = startSlot + col;
			ItemStack stack = new ItemStack(pane);
			Component name = col == 4
				? Component.literal(label).withStyle(labelColor)
				: Component.literal(" ");
			stack.set(DataComponents.CUSTOM_NAME, name);
			this.trustContainer.setItem(slot, stack);
		}
	}

	private ItemStack createPageInfoItem(int trustedCount, int untrustedCount) {
		ItemStack info = new ItemStack(Items.PAPER);
		info.set(DataComponents.CUSTOM_NAME, Component.literal(SafeZoneText.TRUST_MENU_INFO_TITLE).withStyle(ChatFormatting.GOLD));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.literal(SafeZoneText.trustMenuClaimLine(this.claimId)).withStyle(ChatFormatting.YELLOW));
		lore.add(Component.literal(SafeZoneText.TRUST_MENU_INFO_HINT).withStyle(ChatFormatting.GRAY));
		lore.add(Component.literal(trustedCount + " trusted, " + untrustedCount + " other players").withStyle(ChatFormatting.DARK_GRAY));
		if (this.untrustedPageCount > 1) {
			lore.add(Component.literal("Other players: page " + (this.untrustedPage + 1) + " / " + this.untrustedPageCount)
				.withStyle(ChatFormatting.BLUE));
		}
		info.set(DataComponents.LORE, new ItemLore(lore));
		return info;
	}

	private static ItemStack createNavigationItem(net.minecraft.world.item.Item item, String title, String description) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(title).withStyle(ChatFormatting.YELLOW));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(description).withStyle(ChatFormatting.GRAY))));
		return stack;
	}

	private static ItemStack buildPlayerHead(GameProfile profile, String playerName, boolean trusted, boolean online) {
		ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
		skull.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
		skull.set(DataComponents.CUSTOM_NAME,
			Component.literal(playerName).withStyle(trusted ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		skull.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal(SafeZoneText.trustMenuPlayerStatus(trusted))
				.withStyle(trusted ? ChatFormatting.GREEN : ChatFormatting.GRAY),
			Component.literal(SafeZoneText.trustMenuPlayerAction(trusted))
				.withStyle(ChatFormatting.YELLOW),
			Component.literal(SafeZoneText.trustMenuSeenStatus(online))
				.withStyle(ChatFormatting.BLUE)
		)));
		if (trusted) {
			skull.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return skull;
	}

	private void addPlayerInventory(Inventory playerInventory) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				int slot = column + row * 9 + 9;
				this.addSlot(new Slot(playerInventory, slot, 8 + column * 18, 140 + row * 18));
			}
		}

		for (int column = 0; column < 9; column++) {
			this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 198));
		}
	}

	private record TrustEntry(UUID playerId, String playerName, boolean trusted, boolean online, ItemStack displayStack) {
	}
}
