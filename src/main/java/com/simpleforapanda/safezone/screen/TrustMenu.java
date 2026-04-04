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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TrustMenu extends AbstractContainerMenu {
	private static final int ROWS = 6;
	private static final int TOTAL_SLOT_COUNT = ROWS * 9;
	private static final int PLAYER_GRID_SLOT_COUNT = 45;
	private static final int PREVIOUS_PAGE_SLOT = 45;
	private static final int PAGE_INFO_SLOT = 49;
	private static final int NEXT_PAGE_SLOT = 53;

	private final String claimId;
	private final ServerPlayer owner;
	private final SimpleContainer trustContainer;
	private final List<TrustEntry> allEntries = new ArrayList<>();
	private int page;

	public TrustMenu(int syncId, Inventory playerInventory, ServerPlayer owner, String claimId) {
		super(MenuType.GENERIC_9x6, syncId);
		this.claimId = claimId;
		this.owner = owner;
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

		if (slotIndex < PLAYER_GRID_SLOT_COUNT) {
			if (player instanceof ServerPlayer) {
				int entryIndex = this.page * PLAYER_GRID_SLOT_COUNT + slotIndex;
				if (entryIndex < this.allEntries.size()) {
					TrustEntry entry = this.allEntries.get(entryIndex);
					boolean nowTrusted = ClaimManager.getInstance().toggleTrustedPlayer(this.claimId, entry.playerId(), entry.playerName());
					PlayerMessageHelper.sendStatus(this.owner, nowTrusted ? "SHARED" : "LOCKED",
						nowTrusted ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
						SafeZoneText.trustMenuToggleStatus(nowTrusted, entry.playerName(), this.claimId));
					refreshEntries();
				}
			}
			return;
		}

		if (slotIndex == PREVIOUS_PAGE_SLOT && this.page > 0) {
			this.page--;
			refreshEntries();
			return;
		}
		if (slotIndex == NEXT_PAGE_SLOT && this.page + 1 < getPageCount()) {
			this.page++;
			refreshEntries();
			return;
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
		this.allEntries.clear();
		Optional<ClaimData> claim = ClaimManager.getInstance().getClaim(this.claimId);
		if (claim.isEmpty()) {
			this.owner.closeContainer();
			return;
		}

		ClaimData claimData = claim.get();
		claimData.ensureDefaults();
		Map<UUID, TrustEntry> entriesById = new LinkedHashMap<>();
		var server = ClaimManager.getInstance().getServer();
		if (server == null) {
			this.owner.closeContainer();
			return;
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers().stream()
			.filter(player -> !player.getUUID().equals(this.owner.getUUID()))
			.toList();

		for (ServerPlayer player : players) {
			boolean trusted = claimData.isTrusted(player.getUUID());
			entriesById.put(player.getUUID(), new TrustEntry(
				player.getUUID(),
				player.getName().getString(),
				trusted,
				true,
				buildPlayerHead(player.getGameProfile(), player.getName().getString(), trusted, true)
			));
		}

		for (String trustedPlayerId : claimData.trusted) {
			UUID playerId = UUID.fromString(trustedPlayerId);
			if (playerId.equals(this.owner.getUUID()) || entriesById.containsKey(playerId)) {
				continue;
			}

			String playerName = claimData.getTrustedName(trustedPlayerId);
			if (playerName == null || playerName.isBlank()) {
				playerName = playerId.toString();
			}

			entriesById.put(playerId, new TrustEntry(
				playerId,
				playerName,
				true,
				false,
				buildPlayerHead(new GameProfile(playerId, playerName), playerName, true, false)
			));
		}

		this.allEntries.addAll(entriesById.values().stream()
			.sorted(Comparator.comparing(TrustEntry::trusted).reversed()
				.thenComparing(TrustEntry::online).reversed()
				.thenComparing(TrustEntry::playerName, String.CASE_INSENSITIVE_ORDER))
			.toList());

		this.page = Math.max(0, Math.min(this.page, Math.max(0, getPageCount() - 1)));
		int startIndex = this.page * PLAYER_GRID_SLOT_COUNT;

		for (int slot = 0; slot < PLAYER_GRID_SLOT_COUNT; slot++) {
			int entryIndex = startIndex + slot;
			this.trustContainer.setItem(slot, entryIndex < this.allEntries.size() ? this.allEntries.get(entryIndex).displayStack().copy() : ItemStack.EMPTY);
		}
		this.trustContainer.setItem(PREVIOUS_PAGE_SLOT, this.page > 0 ? createNavigationItem(Items.ARROW, "Previous Page", "Go to the previous page") : ItemStack.EMPTY);
		this.trustContainer.setItem(PAGE_INFO_SLOT, createPageInfoItem());
		this.trustContainer.setItem(NEXT_PAGE_SLOT, this.page + 1 < getPageCount() ? createNavigationItem(Items.ARROW, "Next Page", "Go to the next page") : ItemStack.EMPTY);
		for (int slot = PLAYER_GRID_SLOT_COUNT; slot < TOTAL_SLOT_COUNT; slot++) {
			if (slot == PREVIOUS_PAGE_SLOT || slot == PAGE_INFO_SLOT || slot == NEXT_PAGE_SLOT) {
				continue;
			}
			this.trustContainer.setItem(slot, ItemStack.EMPTY);
		}

		this.broadcastFullState();
	}

	private int getPageCount() {
		return Math.max(1, (this.allEntries.size() + PLAYER_GRID_SLOT_COUNT - 1) / PLAYER_GRID_SLOT_COUNT);
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

	private ItemStack createPageInfoItem() {
		long trustedCount = this.allEntries.stream().filter(TrustEntry::trusted).count();
		long offlineTrustedCount = this.allEntries.stream().filter(entry -> entry.trusted() && !entry.online()).count();
		ItemStack info = new ItemStack(Items.PAPER);
		info.set(DataComponents.CUSTOM_NAME, Component.literal(SafeZoneText.TRUST_MENU_INFO_TITLE).withStyle(ChatFormatting.GOLD));
		info.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal(SafeZoneText.trustMenuClaimLine(this.claimId)).withStyle(ChatFormatting.YELLOW),
			Component.literal(SafeZoneText.TRUST_MENU_INFO_HINT).withStyle(ChatFormatting.GRAY),
			Component.literal(SafeZoneText.trustMenuPageLine(this.page + 1, getPageCount())).withStyle(ChatFormatting.BLUE),
			Component.literal(SafeZoneText.trustMenuPlayerCountLine(this.allEntries.size()))
				.withStyle(ChatFormatting.GRAY),
			Component.literal(SafeZoneText.trustMenuTrustedSummary(trustedCount, offlineTrustedCount))
				.withStyle(ChatFormatting.DARK_GRAY)
		)));
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

	private record TrustEntry(UUID playerId, String playerName, boolean trusted, boolean online, ItemStack displayStack) {
	}
}
