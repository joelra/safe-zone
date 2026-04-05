package com.simpleforapanda.safezone.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

final class CommandTextHelper {
	private CommandTextHelper() {
	}

	static Component title(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
	}

	static Component subtitle(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}

	static Component body(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}

	static Component detailLine(String label, String value) {
		return Component.literal(label + ": ").withStyle(ChatFormatting.DARK_GRAY)
			.append(Component.literal(value).withStyle(ChatFormatting.GRAY));
	}

	static Component accent(String text) {
		return Component.literal(text).withStyle(ChatFormatting.YELLOW);
	}

	static Component separator() {
		return Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY);
	}

	static Component spacer() {
		return Component.literal(" ");
	}

	static Component disabledButton(String label) {
		return Component.literal("[" + label + "]").withStyle(ChatFormatting.DARK_GRAY);
	}

	static Component pageControls(int page, int totalPages, String previousCommand, String nextCommand) {
		Component previous = previousCommand == null
			? disabledButton("PREV")
			: runButton("PREV", previousCommand, ChatFormatting.YELLOW, "Go to the previous page");
		Component next = nextCommand == null
			? disabledButton("NEXT")
			: runButton("NEXT", nextCommand, ChatFormatting.YELLOW, "Go to the next page");
		return previous.copy()
			.append(Component.literal(" "))
			.append(accent("Page " + page + "/" + totalPages))
			.append(Component.literal(" "))
			.append(next);
	}

	static Component commandEntry(Component action, String description) {
		return action.copy().append(separator()).append(body(description));
	}

	static Component listEntry(String label, Component actions, String details) {
		return accent(label).copy()
			.append(Component.literal(" "))
			.append(actions)
			.append(separator())
			.append(body(details));
	}

	static Component statusLine(String label, ChatFormatting color, String message) {
		return Component.literal(label).withStyle(color, ChatFormatting.BOLD)
			.append(separator())
			.append(body(message));
	}

	static Component runButton(String label, String command, ChatFormatting color, String hoverText) {
		return Component.literal("[" + label + "]").withStyle(style -> style
			.withColor(color)
			.withUnderlined(true)
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText))));
	}

	static Component suggestButton(String label, String command, ChatFormatting color, String hoverText) {
		return Component.literal("[" + label + "]").withStyle(style -> style
			.withColor(color)
			.withUnderlined(true)
			.withClickEvent(new ClickEvent.SuggestCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText))));
	}
}
