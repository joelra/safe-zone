package com.simpleforapanda.safezone.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.simpleforapanda.safezone.manager.ClaimManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class CommandSuggestionHelper {
	private CommandSuggestionHelper() {
	}

	static CompletableFuture<Suggestions> suggestAllClaimIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		List<String> claimIds = ClaimManager.getInstance().getClaims().stream()
			.map(claim -> claim.claimId)
			.sorted(String.CASE_INSENSITIVE_ORDER)
			.toList();
		return SharedSuggestionProvider.suggest(claimIds, builder);
	}

	static CompletableFuture<Suggestions> suggestOwnedClaimIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			return Suggestions.empty();
		}

		List<String> claimIds = ClaimManager.getInstance().getClaimsForOwner(player.getUUID()).stream()
			.map(claim -> claim.claimId)
			.sorted(String.CASE_INSENSITIVE_ORDER)
			.toList();
		return SharedSuggestionProvider.suggest(claimIds, builder);
	}

	static CompletableFuture<Suggestions> suggestAccessibleClaimIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			return Suggestions.empty();
		}

		List<String> claimIds = ClaimManager.getInstance().getClaims().stream()
			.filter(claim -> claim.owns(player.getUUID()) || claim.isTrusted(player.getUUID()))
			.map(claim -> claim.claimId)
			.sorted(String.CASE_INSENSITIVE_ORDER)
			.toList();
		return SharedSuggestionProvider.suggest(claimIds, builder);
	}
}
