package com.simpleforapanda.safezone.paper.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class PaperTitleMessageHelper {
	private static final Title.Times HINT_TIMES = Title.Times.times(
		Duration.ofMillis(200),
		Duration.ofSeconds(2),
		Duration.ofMillis(500));

	private PaperTitleMessageHelper() {
	}

	public static void showHint(Player player, String title, String subtitle) {
		player.showTitle(Title.title(
			Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD),
			Component.text(subtitle, NamedTextColor.YELLOW),
			HINT_TIMES));
	}
}
