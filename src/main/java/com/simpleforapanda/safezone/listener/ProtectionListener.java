package com.simpleforapanda.safezone.listener;

import com.simpleforapanda.safezone.command.AdminCommand;
import com.simpleforapanda.safezone.data.PermissionResult;
import com.simpleforapanda.safezone.item.ClaimWandHandler;
import com.simpleforapanda.safezone.item.ModItems;
import com.simpleforapanda.safezone.manager.ClaimManager;
import com.simpleforapanda.safezone.manager.PlayerMessageHelper;
import com.simpleforapanda.safezone.text.SafeZoneText;
import com.simpleforapanda.safezone.screen.TrustMenu;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.BlockEvents;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtectionListener {
	private static final long BLOCKED_MESSAGE_COOLDOWN_MILLIS = 250L;
	private static final Map<UUID, Long> LAST_BLOCKED_MESSAGE_AT = new ConcurrentHashMap<>();

	private ProtectionListener() {
	}

	public static void register() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> onAttackBlock(player, level, hand, pos));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> allowBuildAction(player, pos));
		UseBlockCallback.EVENT.register(ProtectionListener::onUseBlock);
		UseEntityCallback.EVENT.register(ProtectionListener::onUseEntity);
		ItemEvents.USE_ON.register(ProtectionListener::onUseItemOnBlock);
	}

	private static InteractionResult onAttackBlock(Player player, Level level, net.minecraft.world.InteractionHand hand, BlockPos pos) {
		if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		if (ModItems.isClaimWand(player.getItemInHand(hand)) && ClaimWandHandler.hasPendingSelection(player.getUUID())) {
			return InteractionResult.FAIL;
		}

		if (!player.isShiftKeyDown() || !ModItems.isClaimWand(player.getItemInHand(hand))) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		var claim = ClaimManager.getInstance().getClaimAt(pos);
		if (claim.isEmpty()) {
			return InteractionResult.PASS;
		}
		PermissionResult permission = ClaimManager.getInstance().canBuild(serverPlayer, pos);
		if (!claim.get().owns(serverPlayer.getUUID()) && permission != PermissionResult.ADMIN_BYPASS) {
			PlayerMessageHelper.sendError(serverPlayer, SafeZoneText.TRUST_OWNER_ONLY);
			return InteractionResult.FAIL;
		}

		MenuProvider provider = new SimpleMenuProvider(
			(syncId, inventory, menuPlayer) -> new TrustMenu(syncId, inventory, serverPlayer, claim.get().claimId),
			net.minecraft.network.chat.Component.literal(SafeZoneText.TRUST_MENU_TITLE)
		);
		serverPlayer.openMenu(provider);
		return InteractionResult.FAIL;
	}

	private static boolean allowBuildAction(Player player, BlockPos pos) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return true;
		}

		if (ModItems.isClaimWand(serverPlayer.getMainHandItem()) && ClaimWandHandler.hasPendingSelection(serverPlayer.getUUID())) {
			return false;
		}

		if (ClaimManager.getInstance().canBuild(serverPlayer, pos) == PermissionResult.DENIED) {
			sendBlockedMessage(serverPlayer);
			return false;
		}

		return true;
	}

	private static InteractionResult onUseBlock(Player player, Level level, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		ItemStack heldItem = player.getItemInHand(hand);
		BlockPos pos = hitResult.getBlockPos();
		if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && ModItems.isClaimWand(heldItem)) {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			return ClaimWandHandler.handleUseOn(serverPlayer, (net.minecraft.server.level.ServerLevel) level, pos);
		}

		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && AdminCommand.tryInspectClaim(serverPlayer, heldItem, pos)) {
			return InteractionResult.FAIL;
		}

		if (isBlockedBlockInteraction(serverPlayer, hand, heldItem, hitResult)) {
			sendBlockedMessage(serverPlayer);
			return InteractionResult.FAIL;
		}

		return InteractionResult.PASS;
	}

	private static InteractionResult onUseEntity(Player player, Level level, net.minecraft.world.InteractionHand hand, net.minecraft.world.entity.Entity entity,
		EntityHitResult hitResult) {
		if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		if (entity instanceof AbstractMinecart && ClaimManager.getInstance().canBuild(serverPlayer, entity.blockPosition()) == PermissionResult.DENIED) {
			sendBlockedMessage(serverPlayer);
			return InteractionResult.FAIL;
		}

		return InteractionResult.PASS;
	}

	private static InteractionResult onUseItemOnBlock(UseOnContext context) {
		if (context.getLevel().isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
			return null;
		}

		if (ModItems.isClaimWand(context.getItemInHand())) {
			return InteractionResult.FAIL;
		}

		if (isBlockedItemUse(player, context)) {
			sendBlockedMessage(player);
			return InteractionResult.FAIL;
		}

		return null;
	}

	private static boolean isBlockedBlockInteraction(ServerPlayer player, net.minecraft.world.InteractionHand hand, ItemStack heldItem, BlockHitResult hitResult) {
		BlockPos clickedPos = hitResult.getBlockPos();
		if (ClaimManager.getInstance().canBuild(player, clickedPos) == PermissionResult.DENIED) {
			return true;
		}

		if (heldItem.getItem() instanceof BlockItem) {
			BlockPos placementPos = new BlockPlaceContext(new UseOnContext(player, hand, hitResult)).getClickedPos();
			return placementPos != clickedPos && ClaimManager.getInstance().canBuild(player, placementPos) == PermissionResult.DENIED;
		}

		if (heldItem.getItem() instanceof BucketItem) {
			BlockPos targetPos = clickedPos.relative(hitResult.getDirection());
			return ClaimManager.getInstance().canBuild(player, targetPos) == PermissionResult.DENIED;
		}

		return false;
	}

	private static boolean isBlockedItemUse(ServerPlayer player, UseOnContext context) {
		BlockPos clickedPos = context.getClickedPos();
		if (ClaimManager.getInstance().canBuild(player, clickedPos) == PermissionResult.DENIED) {
			return true;
		}

		BlockPos targetPos = resolveInteractionTarget(context);
		return targetPos != clickedPos && ClaimManager.getInstance().canBuild(player, targetPos) == PermissionResult.DENIED;
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

	public static void sendBlockedMessage(ServerPlayer player) {
		long now = System.currentTimeMillis();
		Long lastSentAt = LAST_BLOCKED_MESSAGE_AT.get(player.getUUID());
		if (lastSentAt != null && now - lastSentAt < BLOCKED_MESSAGE_COOLDOWN_MILLIS) {
			return;
		}

		LAST_BLOCKED_MESSAGE_AT.put(player.getUUID(), now);
		PlayerMessageHelper.sendWarning(player, SafeZoneText.BLOCKED_BUILD_HINT);
	}
}
