package com.simpleforapanda.safezone.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.item.ModItems;
import com.simpleforapanda.safezone.manager.AdminInspectManager;
import com.simpleforapanda.safezone.manager.AuditLogger;
import com.simpleforapanda.safezone.manager.ClaimManager;
import com.simpleforapanda.safezone.manager.ConfigManager;
import com.simpleforapanda.safezone.manager.NotificationManager;
import com.simpleforapanda.safezone.text.SafeZoneText;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminCommand {
	private static final int CLAIMS_PER_PAGE = 10;
	private static final long REMOVE_ALL_CONFIRM_WINDOW_MILLIS = 10_000L;
	private static final SimpleCommandExceptionType CLAIM_NOT_FOUND = new SimpleCommandExceptionType(Component.literal(SafeZoneText.CLAIM_NOT_FOUND));
	private static final SimpleCommandExceptionType PROFILE_NOT_FOUND = new SimpleCommandExceptionType(Component.literal(SafeZoneText.PROFILE_NOT_FOUND));
	private static final Map<UUID, PendingRemoveAll> PENDING_REMOVE_ALL = new ConcurrentHashMap<>();

	private AdminCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(createRoot("sz"));
			dispatcher.register(createRoot("safezone"));
		});
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createRoot(String literal) {
		return Commands.literal(literal)
			.requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
			.executes(AdminCommand::executeHelp)
			.then(Commands.literal("help").executes(AdminCommand::executeHelp))
			.then(Commands.literal("list")
				.executes(context -> executeList(context, 1))
				.then(Commands.argument("page", IntegerArgumentType.integer(1))
					.executes(context -> executeList(context, IntegerArgumentType.getInteger(context, "page"))))
				.then(Commands.literal("owner")
					.then(Commands.argument("player", GameProfileArgument.gameProfile())
						.executes(context -> executeListOwner(context, 1))
						.then(Commands.argument("page", IntegerArgumentType.integer(1))
							.executes(context -> executeListOwner(context, IntegerArgumentType.getInteger(context, "page"))))))
				.then(Commands.literal("trusted")
					.then(Commands.argument("player", GameProfileArgument.gameProfile())
						.executes(context -> executeListTrusted(context, 1))
						.then(Commands.argument("page", IntegerArgumentType.integer(1))
							.executes(context -> executeListTrusted(context, IntegerArgumentType.getInteger(context, "page")))))))
			.then(Commands.literal("info")
				.executes(AdminCommand::executeInfoAtCurrentPosition)
				.then(Commands.argument("claimId", StringArgumentType.word())
					.suggests(CommandSuggestionHelper::suggestAllClaimIds)
					.executes(AdminCommand::executeInfo)))
			.then(Commands.literal("remove")
				.then(Commands.argument("claimId", StringArgumentType.word())
					.suggests(CommandSuggestionHelper::suggestAllClaimIds)
					.executes(AdminCommand::executeRemove)))
			.then(Commands.literal("removeall")
				.then(Commands.argument("player", GameProfileArgument.gameProfile())
					.executes(AdminCommand::executeRemoveAll)))
			.then(Commands.literal("transfer")
				.then(Commands.argument("claimId", StringArgumentType.word())
					.suggests(CommandSuggestionHelper::suggestAllClaimIds)
					.then(Commands.argument("player", GameProfileArgument.gameProfile())
						.executes(AdminCommand::executeTransfer))))
			.then(Commands.literal("trust")
				.then(Commands.argument("claimId", StringArgumentType.word())
					.suggests(CommandSuggestionHelper::suggestAllClaimIds)
					.then(Commands.argument("player", GameProfileArgument.gameProfile())
						.executes(context -> executeTrust(context, true)))))
			.then(Commands.literal("untrust")
				.then(Commands.argument("claimId", StringArgumentType.word())
					.suggests(CommandSuggestionHelper::suggestAllClaimIds)
					.then(Commands.argument("player", GameProfileArgument.gameProfile())
						.executes(context -> executeTrust(context, false)))))
			.then(Commands.literal("tp")
				.then(Commands.argument("claimId", StringArgumentType.word())
					.suggests(CommandSuggestionHelper::suggestAllClaimIds)
					.executes(AdminCommand::executeTp)))
			.then(Commands.literal("inspect")
				.executes(AdminCommand::executeInspect))
			.then(Commands.literal("notifications")
				.executes(AdminCommand::executeNotifications)
				.then(Commands.literal("purge")
					.executes(AdminCommand::executeNotificationPurge)
					.then(Commands.literal("confirm")
						.executes(AdminCommand::executeNotificationPurgeConfirm))))
			.then(Commands.literal("reload")
				.executes(AdminCommand::executeReload))
			.then(Commands.literal("givewand")
				.executes(AdminCommand::executeGiveWand)
				.then(Commands.argument("player", EntityArgument.player())
					.executes(AdminCommand::executeGiveWand)))
			.then(Commands.literal("limits")
				.then(Commands.argument("player", GameProfileArgument.gameProfile())
					.then(Commands.argument("maxClaims", IntegerArgumentType.integer(1))
						.executes(AdminCommand::executeLimits))));
	}

	private static int executeList(CommandContext<CommandSourceStack> context, int page) {
		return sendClaimList(context.getSource(), ClaimManager.getInstance().getClaims(), page, "Safe Zone claims", "/sz list");
	}

	private static int executeHelp(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		source.sendSuccess(() -> CommandTextHelper.title(SafeZoneText.ADMIN_HELP_TITLE), false);
		source.sendSuccess(() -> CommandTextHelper.subtitle(SafeZoneText.ADMIN_HELP_SUBTITLE), false);
		source.sendSuccess(CommandTextHelper::spacer, false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.runButton("LIST", "/sz list", ChatFormatting.AQUA, "Show all claims"),
			"Browse every safe zone."), false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.suggestButton("INFO", "/sz info ", ChatFormatting.YELLOW, "Fill an info command for a claim ID"),
			"Fill in one claim ID to inspect it."), false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.suggestButton("TP", "/sz tp ", ChatFormatting.AQUA, "Fill a teleport command for a claim ID"),
			"Jump to one safe zone."), false);
		source.sendSuccess(CommandTextHelper::spacer, false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.suggestButton("REMOVE", "/sz remove ", ChatFormatting.RED, "Fill a remove command for a claim ID"),
			"Fill a remove command for one safe zone."), false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.suggestButton("REMOVEALL", "/sz removeall ", ChatFormatting.RED, "Fill a remove-all command for one player"),
			"Remove every safe zone owned by one player. Requires confirmation."), false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.runButton("NOTIFY", "/sz notifications", ChatFormatting.LIGHT_PURPLE,
				"Review pending offline admin notifications"),
			"Check notification status or purge notifications.json after confirming."), false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.runButton("WAND", "/sz givewand", ChatFormatting.GOLD,
				"Give yourself a " + ModItems.claimWandName() + " claim wand"),
			"Recover or hand out the claim wand."), false);
		source.sendSuccess(() -> CommandTextHelper.commandEntry(
			CommandTextHelper.runButton("INSPECT", "/sz inspect", ChatFormatting.GREEN, "Toggle admin inspect mode"),
			"Check safe zones in-world with Shift + right-click and an empty hand."), false);
		source.sendSuccess(CommandTextHelper::spacer, false);
		source.sendSuccess(() -> CommandTextHelper.subtitle(SafeZoneText.ADMIN_HELP_TIP), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int executeListOwner(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
		NameAndId target = getSingleProfile(context, "player");
		return sendClaimList(context.getSource(),
			ClaimManager.getInstance().getClaimsForOwner(target.id()),
			page,
			SafeZoneText.adminClaimListTitle(target.name()),
			"/sz list owner " + target.name());
	}

	private static int executeListTrusted(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
		NameAndId target = getSingleProfile(context, "player");
		return sendClaimList(context.getSource(),
			ClaimManager.getInstance().getClaimsTrustedFor(target.id()),
			page,
			SafeZoneText.adminTrustedListTitle(target.name()),
			"/sz list trusted " + target.name());
	}

	private static int executeInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ClaimData claim = getClaim(context);
		return sendClaimInfo(context.getSource(), claim);
	}

	private static int executeInfoAtCurrentPosition(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		return ClaimManager.getInstance().getClaimAt(player.blockPosition())
			.map(claim -> sendClaimInfo(source, claim))
			.orElseGet(() -> {
				source.sendFailure(CommandTextHelper.statusLine("UNCLAIMED", ChatFormatting.RED,
					SafeZoneText.NO_SAFE_ZONE_HERE));
				return 0;
			});
	}

	private static int executeRemove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ClaimData claim = getClaim(context);
		if (!ClaimManager.getInstance().removeClaim(claim.claimId)) {
			throw CLAIM_NOT_FOUND.create();
		}

		NotificationManager.getInstance().queueClaimRemovedNotification(claim, source.getTextName());
		AuditLogger.getInstance().logAdminAction(source.getTextName(), "REMOVE", claim.claimId, "owner=" + claim.ownerName);
		source.sendSuccess(() -> CommandTextHelper.statusLine("REMOVED", ChatFormatting.RED,
			SafeZoneText.adminRemovedClaim(claim.claimId, claim.ownerName)), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int executeRemoveAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		NameAndId target = getSingleProfile(context, "player");
		List<ClaimData> claims = ClaimManager.getInstance().getClaimsForOwner(target.id());
		if (claims.isEmpty()) {
			source.sendFailure(CommandTextHelper.statusLine("EMPTY", ChatFormatting.RED,
				SafeZoneText.noOwnedClaims(target.name())));
			return 0;
		}

		long now = System.currentTimeMillis();
		PendingRemoveAll pending = PENDING_REMOVE_ALL.get(source.getPlayerOrException().getUUID());
		if (pending == null || !pending.matches(target.id(), now)) {
			PENDING_REMOVE_ALL.put(source.getPlayerOrException().getUUID(),
				new PendingRemoveAll(target.id(), now + REMOVE_ALL_CONFIRM_WINDOW_MILLIS));
			source.sendFailure(CommandTextHelper.statusLine("CONFIRM", ChatFormatting.RED,
				SafeZoneText.adminRemoveAllConfirm(target.name(), claims.size(), 10)));
			return 0;
		}

		for (ClaimData claim : claims) {
			ClaimManager.getInstance().removeClaim(claim.claimId);
			NotificationManager.getInstance().queueClaimRemovedNotification(claim, source.getTextName());
		}

		PENDING_REMOVE_ALL.remove(source.getPlayerOrException().getUUID());
		AuditLogger.getInstance().logAdminAction(source.getTextName(), "REMOVEALL", "-", "owner=" + target.name() + " count=" + claims.size());
		source.sendSuccess(() -> CommandTextHelper.statusLine("REMOVED", ChatFormatting.RED,
			SafeZoneText.adminRemovedAll(target.name(), claims.size())), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int executeTransfer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ClaimData claim = getClaim(context);
		NameAndId target = getSingleProfile(context, "player");

		ClaimManager.getInstance().transferClaim(claim.claimId, target.id(), target.name());
		AuditLogger.getInstance().logAdminAction(source.getTextName(), "TRANSFER", claim.claimId,
			"old=" + claim.ownerName + " new=" + target.name());
		source.sendSuccess(() -> CommandTextHelper.statusLine("TRANSFERRED", ChatFormatting.GREEN,
			SafeZoneText.adminTransferSuccess(claim.claimId, target.name())), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int executeTrust(CommandContext<CommandSourceStack> context, boolean trusted) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ClaimData claim = getClaim(context);
		NameAndId target = getSingleProfile(context, "player");
		if (claim.ownerUuid.equals(target.id().toString())) {
			source.sendFailure(CommandTextHelper.statusLine("UNCHANGED", ChatFormatting.YELLOW,
				SafeZoneText.ownerAlreadyHasBuildAccess()));
			return 0;
		}

		boolean changed = ClaimManager.getInstance().setPlayerTrusted(claim.claimId, target.id(), target.name(), trusted);
		String action = trusted ? "TRUST" : "UNTRUST";
		AuditLogger.getInstance().logAdminAction(source.getTextName(), action, claim.claimId,
			(trusted ? "added=" : "removed=") + target.name());
		source.sendSuccess(() -> CommandTextHelper.statusLine(changed ? "UPDATED" : "UNCHANGED",
			trusted ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
			SafeZoneText.adminTrustStatus(trusted, target.name(), claim.claimId)), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int executeTp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ClaimData claim = getClaim(context);
		int x = claim.getCenter().getX();
		int z = claim.getCenter().getZ();
		net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
		player.teleportTo(level, x + 0.5D, y, z + 0.5D, java.util.Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
		context.getSource().sendSuccess(() -> CommandTextHelper.statusLine("TELEPORTED", ChatFormatting.AQUA,
			SafeZoneText.adminTeleportSuccess(claim.claimId)), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int executeInspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		boolean enabled = AdminInspectManager.toggle(player.getUUID());
		context.getSource().sendSuccess(() -> CommandTextHelper.statusLine(enabled ? "INSPECT ON" : "INSPECT OFF",
			enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
			enabled
				? SafeZoneText.INSPECT_ENABLED_HINT
				: SafeZoneText.INSPECT_EMPTY_HINT), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int executeNotifications(CommandContext<CommandSourceStack> context) {
		NotificationManager notificationManager = NotificationManager.getInstance();
		boolean notificationsEnabled = notificationManager.notificationsEnabled();
		int pendingNotifications = notificationManager.pendingNotificationCount();
		context.getSource().sendSuccess(() -> CommandTextHelper.statusLine(
			notificationsEnabled ? "NOTIFY ON" : "NOTIFY OFF",
			notificationsEnabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
			SafeZoneText.adminNotificationStatus(notificationsEnabled, pendingNotifications)), false);
		if (pendingNotifications > 0) {
			context.getSource().sendSuccess(() -> CommandTextHelper.commandEntry(
				CommandTextHelper.runButton("PURGE", "/sz notifications purge", ChatFormatting.RED,
					"Start purging pending offline admin notifications"),
				SafeZoneText.adminNotificationPurgeHint(pendingNotifications)), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int executeNotificationPurge(CommandContext<CommandSourceStack> context) {
		int pendingNotifications = NotificationManager.getInstance().pendingNotificationCount();
		if (pendingNotifications < 1) {
			context.getSource().sendSuccess(() -> CommandTextHelper.statusLine("EMPTY", ChatFormatting.YELLOW,
				SafeZoneText.adminNotificationPurgeEmpty()), false);
			return Command.SINGLE_SUCCESS;
		}

		context.getSource().sendFailure(CommandTextHelper.statusLine("CONFIRM", ChatFormatting.RED,
			SafeZoneText.adminNotificationPurgeConfirm(pendingNotifications)));
		return 0;
	}

	private static int executeNotificationPurgeConfirm(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		int purgedNotifications = NotificationManager.getInstance().purgeAllNotifications();
		AuditLogger.getInstance().logAdminAction(source.getTextName(), "NOTIFY_PURGE", "-", "count=" + purgedNotifications);
		source.sendSuccess(() -> CommandTextHelper.statusLine(
			purgedNotifications > 0 ? "PURGED" : "EMPTY",
			purgedNotifications > 0 ? ChatFormatting.RED : ChatFormatting.YELLOW,
			SafeZoneText.adminNotificationPurged(purgedNotifications)), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int executeReload(CommandContext<CommandSourceStack> context) {
		try {
			var server = context.getSource().getServer();
			ConfigManager.getInstance().load(server);
			ClaimManager.getInstance().load(server);
			NotificationManager.getInstance().load(server);
			AuditLogger.getInstance().load(server);
			AdminInspectManager.clear();
			context.getSource().sendSuccess(() -> CommandTextHelper.statusLine("RELOADED", ChatFormatting.GREEN,
				SafeZoneText.RELOAD_SUCCESS), true);
			return Command.SINGLE_SUCCESS;
		} catch (RuntimeException exception) {
			com.simpleforapanda.safezone.SafeZone.LOGGER.error("Safe Zone reload failed", exception);
			context.getSource().sendFailure(CommandTextHelper.statusLine("RELOAD FAILED", ChatFormatting.RED,
				SafeZoneText.RELOAD_FAILURE));
			return 0;
		}
	}

	private static int executeGiveWand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getNodes().stream().anyMatch(node -> "player".equals(node.getNode().getName()))
			? EntityArgument.getPlayer(context, "player")
			: context.getSource().getPlayerOrException();
		ItemStack claimWand = ModItems.createClaimWandStack();
		if (!player.getInventory().add(claimWand)) {
			player.drop(claimWand, false);
		}
		context.getSource().sendSuccess(() -> CommandTextHelper.statusLine("WAND", ChatFormatting.GOLD,
			SafeZoneText.adminGiveWandSuccess(ModItems.claimWandName(), player.getName().getString())), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int executeLimits(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		NameAndId target = getSingleProfile(context, "player");
		int maxClaims = IntegerArgumentType.getInteger(context, "maxClaims");
		ClaimManager.getInstance().setPlayerLimit(target.id(), maxClaims);
		AuditLogger.getInstance().logAdminAction(source.getTextName(), "LIMITS", "-", "player=" + target.name() + " maxClaims=" + maxClaims);
		source.sendSuccess(() -> CommandTextHelper.statusLine("LIMIT", ChatFormatting.GREEN,
			SafeZoneText.adminLimitSuccess(target.name(), maxClaims)), true);
		return Command.SINGLE_SUCCESS;
	}

	public static boolean tryInspectClaim(ServerPlayer player, ItemStack heldItem, BlockPos pos) {
		if (!AdminInspectManager.isInspecting(player.getUUID())) {
			return false;
		}
		if (!player.isShiftKeyDown() || !heldItem.isEmpty()) {
			return false;
		}

		var claim = ClaimManager.getInstance().getClaimAt(pos);
		if (claim.isEmpty()) {
			com.simpleforapanda.safezone.manager.PlayerMessageHelper.sendInfo(player, SafeZoneText.NO_SAFE_ZONE_AT_SPOT);
			return true;
		}

		ClaimData claimData = claim.get();
		com.simpleforapanda.safezone.manager.PlayerMessageHelper.sendStatus(player, "INSPECT", ChatFormatting.AQUA,
			SafeZoneText.adminInspectSummary(claimData.claimId, claimData.ownerName, claimData.getWidth(), claimData.getDepth(), claimData.trusted.size()));
		return true;
	}

	private static ClaimData getClaim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String claimId = StringArgumentType.getString(context, "claimId");
		return ClaimManager.getInstance().getClaim(claimId).orElseThrow(CLAIM_NOT_FOUND::create);
	}

	private static NameAndId getSingleProfile(CommandContext<CommandSourceStack> context, String argumentName) throws CommandSyntaxException {
		Collection<NameAndId> profiles = GameProfileArgument.getGameProfiles(context, argumentName);
		if (profiles.isEmpty()) {
			throw PROFILE_NOT_FOUND.create();
		}
		return profiles.iterator().next();
	}

	private static int sendClaimInfo(CommandSourceStack source, ClaimData claim) {
		source.sendSuccess(() -> CommandTextHelper.title(SafeZoneText.claimTitle(claim.claimId)), false);
		source.sendSuccess(() -> CommandTextHelper.detailLine("Owner", claim.ownerName), false);
		source.sendSuccess(() -> CommandTextHelper.detailLine("Size", claim.getWidth() + "x" + claim.getDepth()
			+ " • corners (" + claim.x1 + ", " + claim.z1 + ") to (" + claim.x2 + ", " + claim.z2 + ")"), false);
		source.sendSuccess(() -> CommandTextHelper.detailLine("Trusted", formatTrustedPlayers(source, claim)), false);
		source.sendSuccess(() -> CommandTextHelper.detailLine("Activity", "Created " + formatTimestamp(claim.createdAt)
			+ " • last active " + formatTimestamp(claim.lastActiveAt)), false);
		source.sendSuccess(CommandTextHelper::spacer, false);
		Component actions = CommandTextHelper.runButton("TP", "/sz tp " + claim.claimId, ChatFormatting.AQUA, "Teleport to this claim")
			.copy().append(Component.literal(" "))
			.append(CommandTextHelper.suggestButton("REMOVE", "/sz remove " + claim.claimId, ChatFormatting.RED, "Fill the removal command for this claim"));
		source.sendSuccess(() -> CommandTextHelper.listEntry(SafeZoneText.ACTIONS_LABEL, actions, SafeZoneText.adminClaimInfoActions()), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int sendClaimList(CommandSourceStack source, List<ClaimData> claims, int page, String title, String pageCommandBase) {
		int totalPages = Math.max(1, (claims.size() + CLAIMS_PER_PAGE - 1) / CLAIMS_PER_PAGE);
		int clampedPage = Math.max(1, Math.min(page, totalPages));
		int startIndex = (clampedPage - 1) * CLAIMS_PER_PAGE;
		int endIndex = Math.min(startIndex + CLAIMS_PER_PAGE, claims.size());

		source.sendSuccess(() -> CommandTextHelper.title(title), false);
		source.sendSuccess(() -> CommandTextHelper.subtitle(SafeZoneText.CLICK_ACTIONS_HINT), false);
		if (claims.isEmpty()) {
			source.sendSuccess(CommandTextHelper::spacer, false);
			source.sendSuccess(() -> CommandTextHelper.body(SafeZoneText.LIST_NO_MATCHES), false);
			return Command.SINGLE_SUCCESS;
		}

		source.sendSuccess(CommandTextHelper::spacer, false);
		for (int index = startIndex; index < endIndex; index++) {
			ClaimData claim = claims.get(index);
			Component actions = Component.empty()
				.append(CommandTextHelper.suggestButton("INFO", "/sz info " + claim.claimId, ChatFormatting.YELLOW, "Fill an info command for this claim"))
				.append(Component.literal(" "))
				.append(CommandTextHelper.runButton("TP", "/sz tp " + claim.claimId, ChatFormatting.AQUA, "Teleport to this claim"))
				.append(Component.literal(" "))
				.append(CommandTextHelper.suggestButton("DEL", "/sz remove " + claim.claimId, ChatFormatting.RED, "Fill the removal command for this claim"));
			Component line = CommandTextHelper.listEntry(claim.claimId, actions, claim.ownerName + " • " + claim.getWidth() + "x" + claim.getDepth()
				+ " • center " + claim.getCenter().getX() + ", " + claim.getCenter().getZ());
			source.sendSuccess(() -> line, false);
		}
		source.sendSuccess(CommandTextHelper::spacer, false);
		String previousCommand = clampedPage > 1 ? pageCommandBase + " " + (clampedPage - 1) : null;
		String nextCommand = clampedPage < totalPages ? pageCommandBase + " " + (clampedPage + 1) : null;
		source.sendSuccess(() -> CommandTextHelper.pageControls(clampedPage, totalPages, previousCommand, nextCommand), false);

		return Command.SINGLE_SUCCESS;
	}

	private static String formatTrustedPlayers(CommandSourceStack source, ClaimData claim) {
		if (claim.trusted.isEmpty()) {
			return SafeZoneText.TRUSTED_NONE;
		}

		return claim.trusted.stream()
			.map(playerUuid -> resolveTrustedPlayerName(source, claim, playerUuid))
			.reduce((left, right) -> left + ", " + right)
			.orElse(SafeZoneText.TRUSTED_NONE);
	}

	private static String resolveTrustedPlayerName(CommandSourceStack source, ClaimData claim, String playerUuid) {
		try {
			ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayer(UUID.fromString(playerUuid));
			if (onlinePlayer != null) {
				return onlinePlayer.getName().getString();
			}
		} catch (IllegalArgumentException ignored) {
			// Fall back to stored values below if older data is malformed.
		}

		String trustedName = claim.getTrustedName(playerUuid);
		if (trustedName != null && !trustedName.isBlank()) {
			return trustedName;
		}

		return playerUuid;
	}

	private static String formatTimestamp(long timestamp) {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
	}

	private record PendingRemoveAll(UUID targetId, long expiresAt) {
		private boolean matches(UUID targetId, long now) {
			return this.targetId.equals(targetId) && now <= this.expiresAt;
		}
	}
}
