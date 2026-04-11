package com.simpleforapanda.safezone.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.manager.AuditLogger;
import com.simpleforapanda.safezone.manager.ClaimManager;
import com.simpleforapanda.safezone.item.ClaimWandHandler;
import com.simpleforapanda.safezone.runtime.FabricRuntime;
import com.simpleforapanda.safezone.screen.TrustMenu;
import com.simpleforapanda.safezone.text.SafeZoneText;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerCommand {
	private static final int CLAIMS_PER_PAGE = 10;
	private static final SimpleCommandExceptionType CLAIM_NOT_FOUND = new SimpleCommandExceptionType(Component.literal(SafeZoneText.CLAIM_NOT_FOUND));
	private static final Map<UUID, PendingClaimRemoval> PENDING_REMOVALS = new ConcurrentHashMap<>();
	private static ClaimManager claimManager;
	private static AuditLogger auditLogger;
	private static ClaimWandHandler claimWandHandler;
	private static CommandSuggestionHelper suggestionHelper;

	public PlayerCommand(FabricRuntime runtime) {
		claimManager = runtime.services().claimManager();
		auditLogger = runtime.services().auditLogger();
		claimWandHandler = runtime.services().claimWandHandler();
		suggestionHelper = new CommandSuggestionHelper(claimManager);
	}

	public void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(createRoot("claim"));
			dispatcher.register(createRoot("claims"));
		});
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createRoot(String literal) {
		return Commands.literal(literal)
			.executes(PlayerCommand::executeHelp)
			.then(Commands.literal("help").executes(PlayerCommand::executeHelp))
			.then(Commands.literal("status").executes(PlayerCommand::executeStatus))
			.then(Commands.literal("list")
				.executes(context -> executeList(context, 1))
				.then(Commands.argument("page", IntegerArgumentType.integer(1))
					.executes(context -> executeList(context, IntegerArgumentType.getInteger(context, "page")))))
			.then(Commands.literal("trusted")
				.executes(context -> executeTrustedList(context, 1))
				.then(Commands.argument("page", IntegerArgumentType.integer(1))
					.executes(context -> executeTrustedList(context, IntegerArgumentType.getInteger(context, "page")))))
			.then(Commands.literal("here").executes(PlayerCommand::executeHere))
			.then(Commands.literal("info")
				.executes(PlayerCommand::executeHere)
				.then(Commands.argument("claimId", StringArgumentType.word())
					.suggests(suggestionHelper::suggestAccessibleClaimIds)
					.executes(PlayerCommand::executeInfo)))
			.then(Commands.literal("trust")
				.then(Commands.argument("claimId", StringArgumentType.word())
					.suggests(suggestionHelper::suggestOwnedClaimIds)
					.executes(PlayerCommand::executeTrustMenu)))
			.then(Commands.literal("remove")
				.then(Commands.argument("claimId", StringArgumentType.word())
					.suggests(suggestionHelper::suggestOwnedClaimIds)
					.executes(PlayerCommand::executeRemove)))
			.then(Commands.literal("show").executes(PlayerCommand::executeShow));
	}

	private static int executeHelp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		source.sendSuccess(() -> CommandTextHelper.title(SafeZoneText.PLAYER_HELP_TITLE), false);
		source.sendSuccess(() -> CommandTextHelper.subtitle(SafeZoneText.PLAYER_HELP_SUBTITLE), false);
		source.sendSuccess(CommandTextHelper::spacer, false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.runButton("STATUS", "/claim status", ChatFormatting.GREEN, "Show your Safe Zone claim status"),
			"See your claim counts and current limits."), false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.runButton("LIST", "/claim list", ChatFormatting.AQUA, "Show claims you own"),
			"See the safe zones you own."), false);
		source.sendSuccess(CommandTextHelper::spacer, false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.runButton("TRUSTED", "/claim trusted", ChatFormatting.BLUE, "Show claims that trust you"),
			"See safe zones where you can build."), false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.runButton("HERE", "/claim here", ChatFormatting.YELLOW, "Show info for the claim you're standing in"),
			"Check the safe zone where you are standing."), false);
		source.sendSuccess(CommandTextHelper::spacer, false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.suggestButton("INFO", "/claim info ", ChatFormatting.YELLOW, "Fill an info command for one of your accessible claim IDs"),
			"Fill in one claim ID to inspect it."), false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.suggestButton("TRUST", "/claim trust ", ChatFormatting.GREEN, "Fill a trust-menu command for one of your claim IDs"),
			"Open the sharing menu for one safe zone you own."), false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.suggestButton("REMOVE", "/claim remove ", ChatFormatting.RED, "Fill a removal command for one of your claim IDs"),
			"Fill a remove command. You must repeat it to confirm."), false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.runButton("SHOW", "/claim show", ChatFormatting.LIGHT_PURPLE, "Toggle always-on claim boundary display"),
			"Toggle persistent outline display for your claims."), false);
		source.sendSuccess(CommandTextHelper::spacer, false);
		source.sendSuccess(() -> CommandTextHelper.subtitle(SafeZoneText.PLAYER_HELP_TIP), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int executeStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		int ownedClaims = claimManager.getClaimsForOwner(player.getUUID()).size();
		int trustedClaims = claimManager.getClaimsTrustedFor(player.getUUID()).size();
		int maxClaims = claimManager.getMaxClaims(player.getUUID());
		context.getSource().sendSuccess(() -> CommandTextHelper.title("Safe Zone Status"), false);
		context.getSource().sendSuccess(() -> CommandTextHelper.detailLine("Owned claims", ownedClaims + "/" + maxClaims), false);
		context.getSource().sendSuccess(() -> CommandTextHelper.detailLine("Trusted claims", String.valueOf(trustedClaims)), false);
		context.getSource().sendSuccess(() -> CommandTextHelper.detailLine("Claim wand", claimWandHandler.wandName()), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int executeShow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		boolean enabled = claimManager.toggleClaimShow(player.getUUID());
		String status = enabled ? "ON" : "OFF";
		context.getSource().sendSuccess(() ->
			net.minecraft.network.chat.Component.literal("Claim outlines: ").withStyle(ChatFormatting.GRAY)
				.append(net.minecraft.network.chat.Component.literal(status).withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)),
			false);
		return Command.SINGLE_SUCCESS;
	}

	private static int executeList(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		return sendOwnedClaimList(context.getSource(), claimManager.getClaimsForOwner(player.getUUID()), page, player);
	}

	private static int executeTrustedList(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		return sendTrustedClaimList(context.getSource(), claimManager.getClaimsTrustedFor(player.getUUID()), page, player);
	}

	private static int executeHere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		return claimManager.getClaimAt(player.blockPosition())
			.map(claim -> sendClaimInfo(context.getSource(), player, claim))
			.orElseGet(() -> {
				context.getSource().sendFailure(CommandTextHelper.statusLine("NOT HERE", ChatFormatting.RED,
					SafeZoneText.NO_CLAIM_HERE));
				return 0;
			});
	}

	private static int executeInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		return sendClaimInfo(context.getSource(), player, getAccessibleClaim(player, StringArgumentType.getString(context, "claimId")));
	}

	private static int executeTrustMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ClaimData claim = getOwnedClaim(player, StringArgumentType.getString(context, "claimId"));
		MenuProvider provider = new SimpleMenuProvider(
			(syncId, inventory, menuPlayer) -> new TrustMenu(syncId, inventory, player, claim.claimId, claimManager),
			Component.literal(SafeZoneText.TRUST_MENU_TITLE)
		);
		player.openMenu(provider);
		return Command.SINGLE_SUCCESS;
	}

	private static int executeRemove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ClaimData claim = getOwnedClaim(player, StringArgumentType.getString(context, "claimId"));
		long now = System.currentTimeMillis();
		PendingClaimRemoval pending = PENDING_REMOVALS.get(player.getUUID());
		if (pending == null || !pending.matches(claim.claimId, now)) {
			var gameplayConfig = claimManager.getGameplayConfig();
			PENDING_REMOVALS.put(player.getUUID(),
				new PendingClaimRemoval(claim.claimId, now + gameplayConfig.commandRemoveConfirmWindowMillis()));
			context.getSource().sendFailure(CommandTextHelper.statusLine("CONFIRM", ChatFormatting.RED,
				SafeZoneText.playerRemoveConfirm(claim.claimId, gameplayConfig.commandRemoveConfirmSeconds)));
			return 0;
		}

		PENDING_REMOVALS.remove(player.getUUID());
		if (!claimManager.removeClaim(claim.claimId)) {
			throw CLAIM_NOT_FOUND.create();
		}

		auditLogger.logPlayerAction(player.getName().getString(), "REMOVE", claim.claimId, "owner=" + claim.ownerName);
		context.getSource().sendSuccess(() -> CommandTextHelper.statusLine("REMOVED", ChatFormatting.RED,
			SafeZoneText.playerRemovedClaim(claim.claimId)), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int sendClaimInfo(CommandSourceStack source, ServerPlayer player, ClaimData claim) {
		String role = claim.owns(player.getUUID()) ? "owner" : claim.isTrusted(player.getUUID()) ? "trusted" : "visitor";
		var center = claim.getCenter();
		source.sendSuccess(() -> CommandTextHelper.title(SafeZoneText.claimTitle(claim.claimId)), false);
		source.sendSuccess(() -> CommandTextHelper.detailLine("Owner", claim.ownerName), false);
		source.sendSuccess(() -> CommandTextHelper.detailLine("Your access", role), false);
		source.sendSuccess(() -> CommandTextHelper.detailLine("Size", claim.getWidth() + "x" + claim.getDepth()
			+ " • center " + center.x() + ", " + center.z()), false);
		source.sendSuccess(() -> CommandTextHelper.detailLine("Trusted players", String.valueOf(claim.trusted.size())), false);
		if (claim.owns(player.getUUID())) {
			source.sendSuccess(CommandTextHelper::spacer, false);
			Component actions = CommandTextHelper.runButton("TRUST", "/claim trust " + claim.claimId, ChatFormatting.GREEN,
				"Open the trust menu for this claim")
				.copy()
				.append(Component.literal(" "))
				.append(CommandTextHelper.suggestButton("REMOVE", "/claim remove " + claim.claimId, ChatFormatting.RED,
					"Fill the removal command for this claim"));
			source.sendSuccess(() -> CommandTextHelper.listEntry("Actions", actions, "Open sharing or fill the remove command."), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int sendOwnedClaimList(CommandSourceStack source, List<ClaimData> claims, int page, ServerPlayer player) {
		int totalPages = Math.max(1, (claims.size() + CLAIMS_PER_PAGE - 1) / CLAIMS_PER_PAGE);
		int clampedPage = Math.max(1, Math.min(page, totalPages));
		int startIndex = (clampedPage - 1) * CLAIMS_PER_PAGE;
		int endIndex = Math.min(startIndex + CLAIMS_PER_PAGE, claims.size());
		source.sendSuccess(() -> CommandTextHelper.title(SafeZoneText.OWNED_CLAIMS_TITLE), false);
		source.sendSuccess(() -> CommandTextHelper.subtitle(SafeZoneText.OWNED_CLAIMS_SUBTITLE), false);
		if (claims.isEmpty()) {
			source.sendSuccess(CommandTextHelper::spacer, false);
			source.sendSuccess(() -> CommandTextHelper.body("You do not own any safe zones yet."), false);
			return Command.SINGLE_SUCCESS;
		}

		source.sendSuccess(CommandTextHelper::spacer, false);
		for (int index = startIndex; index < endIndex; index++) {
			ClaimData claim = claims.get(index);
			var center = claim.getCenter();
			Component actions = Component.empty()
				.append(CommandTextHelper.suggestButton("INFO", "/claim info " + claim.claimId, ChatFormatting.YELLOW, "Fill an info command for this claim"))
				.append(Component.literal(" "))
				.append(CommandTextHelper.runButton("TRUST", "/claim trust " + claim.claimId, ChatFormatting.GREEN, "Open the trust menu"))
				.append(Component.literal(" "))
				.append(CommandTextHelper.suggestButton("DEL", "/claim remove " + claim.claimId, ChatFormatting.RED, "Fill the removal command for this claim"));
			Component line = CommandTextHelper.listEntry(claim.claimId, actions, claim.getWidth() + "x" + claim.getDepth()
				+ " • center " + center.x() + ", " + center.z());
			source.sendSuccess(() -> line, false);
		}
		source.sendSuccess(CommandTextHelper::spacer, false);
		String previousCommand = clampedPage > 1 ? "/claim list " + (clampedPage - 1) : null;
		String nextCommand = clampedPage < totalPages ? "/claim list " + (clampedPage + 1) : null;
		source.sendSuccess(() -> CommandTextHelper.pageControls(clampedPage, totalPages, previousCommand, nextCommand), false);

		return Command.SINGLE_SUCCESS;
	}

	private static int sendTrustedClaimList(CommandSourceStack source, List<ClaimData> claims, int page, ServerPlayer player) {
		int totalPages = Math.max(1, (claims.size() + CLAIMS_PER_PAGE - 1) / CLAIMS_PER_PAGE);
		int clampedPage = Math.max(1, Math.min(page, totalPages));
		int startIndex = (clampedPage - 1) * CLAIMS_PER_PAGE;
		int endIndex = Math.min(startIndex + CLAIMS_PER_PAGE, claims.size());
		source.sendSuccess(() -> CommandTextHelper.title(SafeZoneText.TRUSTED_CLAIMS_TITLE), false);
		source.sendSuccess(() -> CommandTextHelper.subtitle(SafeZoneText.TRUSTED_CLAIMS_SUBTITLE), false);
		if (claims.isEmpty()) {
			source.sendSuccess(CommandTextHelper::spacer, false);
			source.sendSuccess(() -> CommandTextHelper.body("No safe zones trust you right now."), false);
			return Command.SINGLE_SUCCESS;
		}

		source.sendSuccess(CommandTextHelper::spacer, false);
		for (int index = startIndex; index < endIndex; index++) {
			ClaimData claim = claims.get(index);
			var center = claim.getCenter();
			Component line = CommandTextHelper.listEntry(claim.claimId,
				CommandTextHelper.suggestButton("INFO", "/claim info " + claim.claimId, ChatFormatting.YELLOW, "Fill an info command for this claim"),
				claim.ownerName + " • " + claim.getWidth() + "x" + claim.getDepth()
					+ " • center " + center.x() + ", " + center.z());
			source.sendSuccess(() -> line, false);
		}
		source.sendSuccess(CommandTextHelper::spacer, false);
		String previousCommand = clampedPage > 1 ? "/claim trusted " + (clampedPage - 1) : null;
		String nextCommand = clampedPage < totalPages ? "/claim trusted " + (clampedPage + 1) : null;
		source.sendSuccess(() -> CommandTextHelper.pageControls(clampedPage, totalPages, previousCommand, nextCommand), false);

		return Command.SINGLE_SUCCESS;
	}

	private static ClaimData getOwnedClaim(ServerPlayer player, String claimId) throws CommandSyntaxException {
		ClaimData claim = claimManager.getClaim(claimId).orElseThrow(CLAIM_NOT_FOUND::create);
		if (!claim.owns(player.getUUID())) {
			throw new SimpleCommandExceptionType(Component.literal(SafeZoneText.OWN_CLAIMS_ONLY)).create();
		}
		return claim;
	}

	private static ClaimData getAccessibleClaim(ServerPlayer player, String claimId) throws CommandSyntaxException {
		ClaimData claim = claimManager.getClaim(claimId).orElseThrow(CLAIM_NOT_FOUND::create);
		if (claim.owns(player.getUUID()) || claim.isTrusted(player.getUUID())) {
			return claim;
		}
		throw new SimpleCommandExceptionType(Component.literal(SafeZoneText.NO_CLAIM_ACCESS)).create();
	}

	private record PendingClaimRemoval(String claimId, long expiresAt) {
		private boolean matches(String claimId, long now) {
			return this.claimId.equals(claimId) && now <= this.expiresAt;
		}
	}
}
