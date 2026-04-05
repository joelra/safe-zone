package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.text.SafeZoneText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerMessageHelper {
	private PlayerMessageHelper() {
	}

	public static void sendInfo(ServerPlayer player, String message) {
		player.sendSystemMessage(prefixed(Component.literal(message).withStyle(ChatFormatting.GRAY)));
	}

	public static void sendSuccess(ServerPlayer player, String message) {
		player.sendSystemMessage(prefixed(Component.literal(message).withStyle(ChatFormatting.GREEN)));
	}

	public static void sendWarning(ServerPlayer player, String message) {
		player.sendSystemMessage(prefixed(Component.literal(message).withStyle(ChatFormatting.YELLOW)));
	}

	public static void sendError(ServerPlayer player, String message) {
		player.sendSystemMessage(prefixed(Component.literal(message).withStyle(ChatFormatting.RED)));
	}

	public static void sendStatus(ServerPlayer player, String label, ChatFormatting labelColor, String message) {
		player.sendSystemMessage(prefixed(Component.literal(label).withStyle(labelColor, ChatFormatting.BOLD)
			.append(separator())
			.append(Component.literal(message).withStyle(ChatFormatting.GRAY))));
	}

	public static void sendStep(ServerPlayer player, String message) {
		player.sendSystemMessage(prefixed(Component.literal(SafeZoneText.STEP_LABEL).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
			.append(separator())
			.append(Component.literal(message).withStyle(ChatFormatting.GRAY))));
	}

	public static MutableComponent separator() {
		return Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY);
	}

	public static MutableComponent prefixed(Component component) {
		return Component.literal(SafeZoneText.SAFE_ZONE_PREFIX).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).append(component);
	}
}
