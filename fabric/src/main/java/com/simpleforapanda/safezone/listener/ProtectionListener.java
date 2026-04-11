package com.simpleforapanda.safezone.listener;

import com.simpleforapanda.safezone.data.PermissionResult;
import com.simpleforapanda.safezone.item.ClaimWandHandler;
import com.simpleforapanda.safezone.manager.ClaimManager;
import com.simpleforapanda.safezone.manager.ClaimVisualizationManager;
import com.simpleforapanda.safezone.manager.PlayerMessageHelper;
import com.simpleforapanda.safezone.protection.RideableEntityClassifier;
import com.simpleforapanda.safezone.runtime.FabricAdminInspectService;
import com.simpleforapanda.safezone.runtime.FabricRuntime;
import com.simpleforapanda.safezone.text.SafeZoneText;
import com.simpleforapanda.safezone.screen.TrustMenu;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.BlockEvents;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtectionListener {
	private static final long BLOCKED_MESSAGE_COOLDOWN_MILLIS = 250L;
	private static ProtectionListener instance;
	private final Map<UUID, Long> lastBlockedMessageAt = new ConcurrentHashMap<>();
	private final ClaimManager claimManager;
	private final ClaimVisualizationManager claimVisualizationManager;
	private final FabricAdminInspectService adminInspectService;
	private final ClaimWandHandler claimWandHandler;
	private final RideableEntityClassifier<Entity> rideableEntityClassifier;

	public ProtectionListener(FabricRuntime runtime) {
		instance = this;
		this.claimManager = runtime.services().claimManager();
		this.claimVisualizationManager = runtime.services().claimVisualizationManager();
		this.adminInspectService = runtime.services().adminInspectService();
		this.claimWandHandler = runtime.services().claimWandHandler();
		this.rideableEntityClassifier = runtime.services().rideableEntityClassifier();
	}

	public void register() {
		AttackBlockCallback.EVENT.register(this::onAttackBlock);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> this.allowBuildAction(player, pos));
		UseBlockCallback.EVENT.register(this::onUseBlock);
		UseItemCallback.EVENT.register(this::onUseItem);
		UseEntityCallback.EVENT.register(this::onUseEntity);
		ItemEvents.USE_ON.register(this::onUseItemOnBlock);
	}

	private InteractionResult onAttackBlock(Player player, Level level, net.minecraft.world.InteractionHand hand, BlockPos pos, Direction direction) {
		if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		if (this.claimWandHandler.isClaimWand(player.getItemInHand(hand)) && this.claimWandHandler.hasPendingSelection(player.getUUID())) {
			return InteractionResult.FAIL;
		}

		if (!player.isShiftKeyDown() || !this.claimWandHandler.isClaimWand(player.getItemInHand(hand))) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		var claim = claimManager.getClaimAt(pos);
		if (claim.isEmpty()) {
			return InteractionResult.PASS;
		}
		PermissionResult permission = claimManager.canBuild(serverPlayer, pos);
		if (!claim.get().owns(serverPlayer.getUUID()) && permission != PermissionResult.ADMIN_BYPASS) {
			PlayerMessageHelper.sendError(serverPlayer, SafeZoneText.TRUST_OWNER_ONLY);
			return InteractionResult.FAIL;
		}

		MenuProvider provider = new SimpleMenuProvider(
			(syncId, inventory, menuPlayer) -> new TrustMenu(syncId, inventory, serverPlayer, claim.get().claimId, this.claimManager),
			net.minecraft.network.chat.Component.literal(SafeZoneText.TRUST_MENU_TITLE)
		);
		serverPlayer.openMenu(provider);
		return InteractionResult.FAIL;
	}

	private boolean allowBuildAction(Player player, BlockPos pos) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return true;
		}

		if (this.claimWandHandler.isClaimWand(serverPlayer.getMainHandItem()) && this.claimWandHandler.hasPendingSelection(serverPlayer.getUUID())) {
			return false;
		}

		if (this.claimManager.canBuild(serverPlayer, pos) == PermissionResult.DENIED) {
			this.sendBlockedMessage(serverPlayer);
			return false;
		}

		return true;
	}

	private InteractionResult onUseBlock(Player player, Level level, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		ItemStack heldItem = player.getItemInHand(hand);
		BlockPos pos = hitResult.getBlockPos();
		if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && this.claimWandHandler.isClaimWand(heldItem)) {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			InteractionResult result = this.claimWandHandler.handleUseOn(serverPlayer, (net.minecraft.server.level.ServerLevel) level, pos);
			if (result == InteractionResult.SUCCESS) {
				this.claimVisualizationManager.refreshPlayer(serverPlayer);
			}
			return result;
		}

		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		if (hand == net.minecraft.world.InteractionHand.MAIN_HAND
			&& this.adminInspectService.tryInspectClaim(this.claimManager, serverPlayer, heldItem, pos)) {
			return InteractionResult.FAIL;
		}

		if (isBlockedBlockInteraction(serverPlayer, hand, heldItem, hitResult)) {
			this.handleDenyItemUse(serverPlayer);
			return InteractionResult.FAIL;
		}

		return InteractionResult.PASS;
	}

	private InteractionResult onUseItem(Player player, Level level, net.minecraft.world.InteractionHand hand) {
		ItemStack heldItem = player.getItemInHand(hand);
		if (hand != net.minecraft.world.InteractionHand.MAIN_HAND || !this.claimWandHandler.isClaimWand(heldItem)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		// Guard: if a block is within vanilla reach, UseBlockCallback already handled this click.
		double vanillaReach = player.blockInteractionRange();
		HitResult vanillaHit = player.pick(vanillaReach, 0.0F, false);
		if (vanillaHit.getType() == HitResult.Type.BLOCK) {
			return InteractionResult.SUCCESS;
		}
		double range = this.claimManager.getGameplayConfig().effectiveWandSelectionRange();
		HitResult hitResult = player.pick(range, 0.0F, false);
		if (hitResult instanceof BlockHitResult blockHit && hitResult.getType() == HitResult.Type.BLOCK) {
			InteractionResult result = this.claimWandHandler.handleUseOn(serverPlayer, (net.minecraft.server.level.ServerLevel) level, blockHit.getBlockPos());
			if (result == InteractionResult.SUCCESS) {
				this.claimVisualizationManager.refreshPlayer(serverPlayer);
			}
		}
		return InteractionResult.SUCCESS;
	}

	private InteractionResult onUseEntity(Player player, Level level, net.minecraft.world.InteractionHand hand, net.minecraft.world.entity.Entity entity,
		EntityHitResult hitResult) {
		if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		if (this.rideableEntityClassifier.isRideable(entity) && this.claimManager.canBuild(serverPlayer, entity.blockPosition()) == PermissionResult.DENIED) {
			this.sendBlockedMessage(serverPlayer);
			return InteractionResult.FAIL;
		}

		return InteractionResult.PASS;
	}

	private InteractionResult onUseItemOnBlock(UseOnContext context) {
		if (context.getLevel().isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
			return null;
		}

		if (this.claimWandHandler.isClaimWand(context.getItemInHand())) {
			return InteractionResult.FAIL;
		}

		if (isBlockedItemUse(player, context)) {
			this.handleDenyItemUse(player);
			return InteractionResult.FAIL;
		}

		return null;
	}

	private boolean isBlockedBlockInteraction(ServerPlayer player, net.minecraft.world.InteractionHand hand, ItemStack heldItem, BlockHitResult hitResult) {
		BlockPos clickedPos = hitResult.getBlockPos();
		if (usesPlacementTarget(heldItem)) {
			BlockPos targetPos = resolveInteractionTarget(new UseOnContext(player, hand, hitResult));
			return this.claimManager.canBuild(player, targetPos) == PermissionResult.DENIED;
		}

		return this.claimManager.canBuild(player, clickedPos) == PermissionResult.DENIED;
	}

	private boolean isBlockedItemUse(ServerPlayer player, UseOnContext context) {
		BlockPos clickedPos = context.getClickedPos();
		if (usesPlacementTarget(context.getItemInHand())) {
			BlockPos targetPos = resolveInteractionTarget(context);
			return this.claimManager.canBuild(player, targetPos) == PermissionResult.DENIED;
		}

		return this.claimManager.canBuild(player, clickedPos) == PermissionResult.DENIED;
	}

	private static BlockPos resolveInteractionTarget(UseOnContext context) {
		if (context.getItemInHand().getItem() instanceof BlockItem) {
			return new BlockPlaceContext(context).getClickedPos();
		}

		if (context.getItemInHand().getItem() instanceof BucketItem) {
			return context.getClickedPos().relative(context.getClickedFace());
		}

		return context.getClickedPos();
	}

	private static boolean usesPlacementTarget(ItemStack heldItem) {
		return heldItem.getItem() instanceof BlockItem || heldItem.getItem() instanceof BucketItem;
	}

	private void sendBlockedMessage(ServerPlayer player) {
		long now = System.currentTimeMillis();
		Long lastSentAt = this.lastBlockedMessageAt.get(player.getUUID());
		if (lastSentAt != null && now - lastSentAt < BLOCKED_MESSAGE_COOLDOWN_MILLIS) {
			return;
		}

		this.lastBlockedMessageAt.put(player.getUUID(), now);
		PlayerMessageHelper.sendWarning(player, SafeZoneText.BLOCKED_BUILD_HINT);
	}

	public static void denyItemUse(ServerPlayer player) {
		if (instance != null) {
			instance.handleDenyItemUse(player);
		}
	}

	private void handleDenyItemUse(ServerPlayer player) {
		this.sendBlockedMessage(player);
		player.containerMenu.sendAllDataToRemote();
		if (player.containerMenu != player.inventoryMenu) {
			player.inventoryMenu.sendAllDataToRemote();
		}
	}
}
