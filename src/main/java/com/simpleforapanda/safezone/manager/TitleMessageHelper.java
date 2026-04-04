package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.text.SafeZoneText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public final class TitleMessageHelper {
	private static final int FADE_IN_TICKS = 4;
	private static final int STAY_TICKS = 40;
	private static final int FADE_OUT_TICKS = 10;

	private TitleMessageHelper() {
	}

	public static void showHint(ServerPlayer player, String title, String subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(FADE_IN_TICKS, STAY_TICKS, FADE_OUT_TICKS));
		player.connection.send(new ClientboundSetTitleTextPacket(
			Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
			Component.literal(subtitle).withStyle(ChatFormatting.YELLOW)));
	}
}
