package com.simpleforapanda.safezone.paper.command;

import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.SafeZoneConfig;
import com.simpleforapanda.safezone.paper.listener.PaperClaimWandSupport;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import com.simpleforapanda.safezone.paper.runtime.PaperRuntime;
import com.simpleforapanda.safezone.paper.runtime.PaperRuntimeStatus;
import com.simpleforapanda.safezone.paper.text.PaperText;
import com.simpleforapanda.safezone.text.SafeZoneText;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

public final class SafeZoneAdminCommand implements CommandExecutor, TabCompleter {
	private static final List<String> SUBCOMMANDS = List.of(
		"help",
		"status",
		"list",
		"info",
		"here",
		"tp",
		"inspect",
		"notifications",
		"givewand",
		"limits",
		"transfer",
		"trust",
		"untrust",
		"remove",
		"delete",
		"removeall",
		"reload");

	private final PaperRuntime runtime;

	public SafeZoneAdminCommand(PaperRuntime runtime) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			sendHelp(sender, label);
			return true;
		}

		String subcommand = args[0].toLowerCase(Locale.ROOT);
		switch (subcommand) {
			case "help" -> sendHelp(sender, label);
			case "status" -> sendStatus(sender);
			case "list" -> sendClaimList(sender, args);
			case "info" -> sendClaimInfo(sender, args);
			case "here" -> sendClaimAtCurrentPosition(sender);
			case "tp" -> teleportToClaim(sender, args);
			case "inspect" -> inspectClaim(sender, args);
			case "notifications" -> manageNotifications(sender, args, label);
			case "givewand" -> giveWand(sender, args);
			case "limits" -> manageLimits(sender, args);
			case "transfer" -> transferClaim(sender, args);
			case "trust" -> setTrust(sender, args, true);
			case "untrust" -> setTrust(sender, args, false);
			case "remove", "delete" -> removeClaim(sender, args);
			case "removeall" -> removeAllClaims(sender, args, label);
			case "reload" -> reload(sender);
			default -> sender.sendMessage(text(SafeZoneText.unknownSubcommand(label), RED));
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return filterByPrefix(SUBCOMMANDS, args[0]);
		}

		String subcommand = args[0].toLowerCase(Locale.ROOT);
		return switch (subcommand) {
			case "info", "inspect", "tp", "remove", "delete" -> args.length == 2
				? filterByPrefix(this.runtime.services().claimStore().getClaimIds(), args[1])
				: List.of();
			case "notifications" -> {
				if (args.length == 2) {
					yield filterByPrefix(List.of("purge"), args[1]);
				}
				if (args.length == 3 && "purge".equalsIgnoreCase(args[1])) {
					yield filterByPrefix(List.of("confirm"), args[2]);
				}
				yield List.of();
			}
			case "transfer", "trust", "untrust" -> {
				if (args.length == 2) {
					yield filterByPrefix(this.runtime.services().claimStore().getClaimIds(), args[1]);
				}
				if (args.length == 3) {
					yield playerSuggestions(args[2]);
				}
				yield List.of();
			}
			case "givewand" -> args.length == 2 ? playerSuggestions(args[1]) : List.of();
			case "limits" -> {
				if (args.length == 2) {
					yield playerSuggestions(args[1]);
				}
				if (args.length == 3) {
					yield filterByPrefix(List.of("clear"), args[2]);
				}
				yield List.of();
			}
			case "removeall" -> {
				if (args.length == 2) {
					yield playerSuggestions(args[1]);
				}
				if (args.length == 3) {
					yield filterByPrefix(List.of("confirm"), args[2]);
				}
				yield List.of();
			}
			case "list" -> {
				if (args.length == 2) {
					yield filterByPrefix(List.of("owner", "trusted"), args[1]);
				}
				if (args.length == 3 && ("owner".equalsIgnoreCase(args[1]) || "trusted".equalsIgnoreCase(args[1]))) {
					yield playerSuggestions(args[2]);
				}
				yield List.of();
			}
			default -> List.of();
		};
	}

	private void sendHelp(CommandSender sender, String label) {
		var lines = PaperText.adminHelpLines(label);
		sender.sendMessage(text(lines.getFirst(), AQUA));
		for (int index = 1; index < lines.size(); index++) {
			sender.sendMessage(text(lines.get(index), GRAY));
		}
	}

	private void sendStatus(CommandSender sender) {
		PaperRuntimeStatus status = this.runtime.status();
		SafeZoneConfig config = status.config();
		sender.sendMessage(text(PaperText.adminRuntimeStatusTitle(), AQUA));
		sender.sendMessage(text("Plugin folder: " + status.pluginDirectory(), GRAY));
		sender.sendMessage(text("Config file: " + status.configFile(), GRAY));
		sender.sendMessage(text("Claims file: " + status.claimsFile(), GRAY));
		sender.sendMessage(text("Player limits file: " + status.playerLimitsFile(), GRAY));
		sender.sendMessage(text("Starter kit file: " + status.starterKitRecipientsFile(), GRAY));
		sender.sendMessage(text("Notifications file: " + status.notificationsFile(), GRAY));
		sender.sendMessage(text("Audit log file: " + status.auditLogFile(), GRAY));
		sender.sendMessage(text("Claims: %d across %d owners".formatted(status.claimCount(), status.ownerCount()), GREEN));
		sender.sendMessage(text(
			"Overrides: %d player limits, %d starter kit records, %d queued notifications"
				.formatted(status.playerLimitOverrideCount(), status.starterKitRecipientCount(), status.notificationCount()),
			GRAY));
		sender.sendMessage(text(
			"Defaults: max=%d, size=%dx%d, gap=%d, expiry=%dd"
				.formatted(
					config.gameplay.defaultMaxClaims,
					config.gameplay.maxClaimWidth,
					config.gameplay.maxClaimDepth,
					config.gameplay.effectiveMinDistance(),
					config.gameplay.claimExpiryDays),
			YELLOW));
		sender.sendMessage(text(
			"Backups: create=%s, recover=%s, notifications=%s, audit=%s"
				.formatted(
					config.ops.createDataBackups,
					config.ops.recoverFromBackupOnLoadFailure,
					config.gameplay.notificationsEnabled,
					config.ops.auditLogEnabled),
			GRAY));
	}

	private void sendClaimList(CommandSender sender, String[] args) {
		if (args.length >= 2 && ("owner".equalsIgnoreCase(args[1]) || "trusted".equalsIgnoreCase(args[1]))) {
			sendFilteredClaimList(sender, args);
			return;
		}

		Integer page = parsePage(sender, args, 1);
		if (page == null) {
			return;
		}

		sendClaimList(sender, this.runtime.services().claimStore().getClaims(), page, "Loaded claims", true);
	}

	private void sendFilteredClaimList(CommandSender sender, String[] args) {
		if (args.length < 3) {
			sender.sendMessage(text(PaperText.usageListFilter(), RED));
			return;
		}

		PlayerReference target = resolvePlayerReference(sender, args[2]);
		if (target == null) {
			return;
		}

		Integer page = parsePage(sender, args, 3);
		if (page == null) {
			return;
		}

		boolean ownerView = "owner".equalsIgnoreCase(args[1]);
		List<ClaimData> claims = ownerView
			? this.runtime.services().claimStore().getClaimsForOwner(target.id())
			: this.runtime.services().claimStore().getClaimsTrustedFor(target.id());
		String title = ownerView ? "Claims owned by " + target.name() : "Claims trusting " + target.name();
		sendClaimList(sender, claims, page, title, !ownerView);
	}

	private void sendClaimInfo(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sendClaimAtCurrentPosition(sender);
			return;
		}

		Optional<ClaimData> claim = this.runtime.services().claimStore().getClaim(args[1]);
		if (claim.isEmpty()) {
			sender.sendMessage(text(SafeZoneText.CLAIM_NOT_FOUND, RED));
			return;
		}

		sendClaimDetails(sender, claim.get(), sender instanceof Player player
			? PaperCommandSupport.describeAccess(claim.get(), player, true)
			: "admin-read");
	}

	private void inspectClaim(CommandSender sender, String[] args) {
		if (args.length >= 2) {
			sendClaimInfo(sender, new String[] { "info", args[1] });
			return;
		}

		if (!(sender instanceof Player player)) {
			sender.sendMessage(text(PaperText.usageInspect(), RED));
			return;
		}

		boolean enabled = this.runtime.services().adminInspectService().toggle(player.getUniqueId());
		sender.sendMessage(text(
			enabled
				? SafeZoneText.INSPECT_ENABLED_HINT
				: SafeZoneText.INSPECT_EMPTY_HINT,
			enabled ? GREEN : YELLOW));
	}

	private void teleportToClaim(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(text(PaperText.playerOnlyTp(), RED));
			return;
		}
		if (args.length < 2) {
			sender.sendMessage(text(PaperText.usageTp(), RED));
			return;
		}

		ClaimData claim = getClaim(sender, args[1]);
		if (claim == null) {
			return;
		}

		Optional<org.bukkit.World> claimWorld = this.runtime.services().claimStore().getClaimWorld();
		if (claimWorld.isEmpty()) {
			sender.sendMessage(text(PaperText.noMainOverworld(), RED));
			return;
		}

		Location destination = resolveTeleportLocation(claimWorld.get(), claim);
		player.teleport(destination);
		sender.sendMessage(text(SafeZoneText.adminTeleportSuccess(claim.claimId), GREEN));
	}

	private void manageNotifications(CommandSender sender, String[] args, String label) {
		if (args.length < 2) {
			sendNotificationStatus(sender, label);
			return;
		}
		if (!"purge".equalsIgnoreCase(args[1])) {
			sender.sendMessage(text(PaperText.notificationsUsage(label), RED));
			return;
		}

		int pendingNotifications = this.runtime.services().notificationStore().count();
		if (pendingNotifications < 1) {
			sender.sendMessage(text(SafeZoneText.adminNotificationPurgeEmpty(), YELLOW));
			return;
		}
		if (args.length < 3 || !"confirm".equalsIgnoreCase(args[2])) {
			sender.sendMessage(text(
				"Confirm purge of " + pendingNotifications + " queued notifications with /" + label + " notifications purge confirm",
				RED));
			return;
		}

		SafeZoneConfig config = this.runtime.services().configService().current();
		int purgedNotifications = this.runtime.services().notificationStore().purgeAllNotifications(config);
		this.runtime.services().auditLogger().logAdminAction(sender.getName(), "NOTIFY_PURGE", "-", "count=" + purgedNotifications);
		sender.sendMessage(text(
			SafeZoneText.adminNotificationPurged(purgedNotifications),
			purgedNotifications > 0 ? RED : YELLOW));
	}

	private void sendNotificationStatus(CommandSender sender, String label) {
		SafeZoneConfig config = this.runtime.services().configService().current();
		int pendingNotifications = this.runtime.services().notificationStore().count();
		sender.sendMessage(text(
			"Offline admin notifications: " + (config.gameplay.notificationsEnabled ? "enabled" : "disabled")
				+ " • queued " + pendingNotifications,
			config.gameplay.notificationsEnabled ? GREEN : YELLOW));
		if (pendingNotifications > 0) {
			sender.sendMessage(text(
				"Run /" + label + " notifications purge confirm to clear them.",
				GRAY));
		}
	}

	private void sendClaimAtCurrentPosition(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(text(PaperText.consoleClaimInfoHint(), RED));
			return;
		}

		this.runtime.services().claimStore().getClaimAt(player.getLocation())
			.ifPresentOrElse(
				claim -> sendClaimDetails(sender, claim, PaperCommandSupport.describeAccess(claim, player, true)),
				() -> sender.sendMessage(text(SafeZoneText.NO_SAFE_ZONE_HERE, RED)));
	}

	private void giveWand(CommandSender sender, String[] args) {
		Player target;
		if (args.length >= 2) {
			target = Bukkit.getPlayerExact(args[1]);
			if (target == null) {
				target = Bukkit.getOnlinePlayers().stream()
					.filter(player -> player.getName().equalsIgnoreCase(args[1]))
					.findFirst()
					.orElse(null);
			}
			if (target == null) {
				sender.sendMessage(text(PaperText.targetPlayerMustBeOnline(), RED));
				return;
			}
		} else if (sender instanceof Player player) {
			target = player;
		} else {
			sender.sendMessage(text(PaperText.usageGiveWand(), RED));
			return;
		}

		SafeZoneConfig config = this.runtime.services().configService().current();
		ItemStack claimWand = PaperClaimWandSupport.createClaimWand(config.gameplay);
		var leftovers = target.getInventory().addItem(claimWand);
		if (!leftovers.isEmpty()) {
			for (ItemStack item : leftovers.values()) {
				target.getWorld().dropItemNaturally(target.getLocation(), item);
			}
		}

		this.runtime.services().auditLogger().logAdminAction(
			sender.getName(),
			"GIVEWAND",
			"-",
			"player=" + target.getName() + " item=" + PaperClaimWandSupport.claimWandName(config.gameplay));
		sender.sendMessage(text(SafeZoneText.adminGiveWandSuccess(PaperClaimWandSupport.claimWandName(config.gameplay), target.getName()), GREEN));
		if (!sender.equals(target)) {
			target.sendMessage(text(PaperText.adminGiftClaimWandNotice(), AQUA));
		}
	}

	private void manageLimits(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(text(PaperText.usageLimits(), RED));
			return;
		}

		PlayerReference target = resolvePlayerReference(sender, args[1]);
		if (target == null) {
			return;
		}

		PaperClaimStore claimStore = this.runtime.services().claimStore();
		SafeZoneConfig config = this.runtime.services().configService().current();
		if (args.length == 2) {
			Optional<Integer> override = claimStore.getPlayerLimitOverride(target.id());
			int effectiveLimit = claimStore.getMaxClaims(target.id(), config.gameplay.defaultMaxClaims);
			int ownedClaims = claimStore.getClaimsForOwner(target.id()).size();
			sender.sendMessage(text(PaperText.claimLimitsTitle(target.name()), AQUA));
			sender.sendMessage(text(PaperText.ownedClaimsLine(ownedClaims), GRAY));
			sender.sendMessage(text(PaperText.defaultLimitLine(config.gameplay.defaultMaxClaims), GRAY));
			sender.sendMessage(text(PaperText.overrideLine(override.map(String::valueOf).orElse(SafeZoneText.TRUSTED_NONE)), GRAY));
			sender.sendMessage(text(PaperText.effectiveLimitLine(effectiveLimit), GREEN));
			return;
		}

		if ("clear".equalsIgnoreCase(args[2])) {
			claimStore.clearPlayerLimit(target.id());
			this.runtime.services().auditLogger().logAdminAction(sender.getName(), "LIMITS_CLEAR", "-", "player=" + target.name());
			sender.sendMessage(text(PaperText.clearedLimitOverride(target.name()), GREEN));
			return;
		}

		Integer maxClaims = parsePositiveInteger(sender, args[2], "Claim limit");
		if (maxClaims == null) {
			return;
		}

		claimStore.setPlayerLimit(target.id(), maxClaims);
		this.runtime.services().auditLogger().logAdminAction(
			sender.getName(),
			"LIMITS",
			"-",
			"player=" + target.name() + " maxClaims=" + maxClaims);
		sender.sendMessage(text(SafeZoneText.adminLimitSuccess(target.name(), maxClaims), GREEN));
	}

	private void transferClaim(CommandSender sender, String[] args) {
		if (args.length < 3) {
			sender.sendMessage(text(PaperText.usageTransfer(), RED));
			return;
		}

		ClaimData claim = getClaim(sender, args[1]);
		if (claim == null) {
			return;
		}

		PlayerReference target = resolvePlayerReference(sender, args[2]);
		if (target == null) {
			return;
		}
		if (claim.ownerUuid.equals(target.id().toString())) {
			sender.sendMessage(text(PaperText.alreadyOwnsClaim(target.name(), claim.claimId), YELLOW));
			return;
		}

		String previousOwnerName = claim.ownerName;
		String previousOwnerUuid = claim.ownerUuid;
		this.runtime.services().claimStore().transferClaim(claim.claimId, target.id(), target.name());
		SafeZoneConfig config = this.runtime.services().configService().current();
		this.runtime.services().notificationStore().queueMessage(
			previousOwnerUuid,
			previousOwnerName,
			sender.getName(),
			PaperText.transferredClaimFromAdmin(claim.claimId, target.name()),
			config);
		this.runtime.services().notificationStore().queueMessage(
			target.id().toString(),
			target.name(),
			sender.getName(),
			PaperText.transferredClaimToYou(claim.claimId),
			config);
		this.runtime.services().auditLogger().logAdminAction(
			sender.getName(),
			"TRANSFER",
			claim.claimId,
			"old=" + previousOwnerName + " new=" + target.name());
		sender.sendMessage(text(SafeZoneText.adminTransferSuccess(claim.claimId, target.name()), GREEN));
	}

	private void setTrust(CommandSender sender, String[] args, boolean trusted) {
		if (args.length < 3) {
			sender.sendMessage(text(PaperText.usageTrustCommand(trusted), RED));
			return;
		}

		ClaimData claim = getClaim(sender, args[1]);
		if (claim == null) {
			return;
		}

		PlayerReference target = resolvePlayerReference(sender, args[2]);
		if (target == null) {
			return;
		}
		if (claim.ownerUuid.equals(target.id().toString())) {
			sender.sendMessage(text(SafeZoneText.ownerAlreadyHasBuildAccess(), YELLOW));
			return;
		}

		boolean changed = this.runtime.services().claimStore().setPlayerTrusted(claim.claimId, target.id(), target.name(), trusted);
		this.runtime.services().auditLogger().logAdminAction(
			sender.getName(),
			trusted ? "TRUST" : "UNTRUST",
			claim.claimId,
			"player=" + target.name() + " changed=" + changed);
		if (changed) {
			sender.sendMessage(text(PaperText.adminTrustChanged(trusted, target.name(), claim.claimId), GREEN));
			return;
		}

		sender.sendMessage(text(PaperText.adminTrustAlreadyState(trusted, target.name(), claim.claimId), YELLOW));
	}

	private void removeClaim(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(text(PaperText.usageAdminRemove(), RED));
			return;
		}

		ClaimData claim = getClaim(sender, args[1]);
		if (claim == null) {
			return;
		}

		if (!this.runtime.services().claimStore().removeClaim(claim.claimId)) {
			sender.sendMessage(text(SafeZoneText.CLAIM_NOT_FOUND, RED));
			return;
		}

		SafeZoneConfig config = this.runtime.services().configService().current();
		this.runtime.services().notificationStore().queueClaimRemovedNotification(claim, sender.getName(), config);
		this.runtime.services().auditLogger().logAdminAction(sender.getName(), "REMOVE", claim.claimId, "owner=" + claim.ownerName);
		sender.sendMessage(text(SafeZoneText.adminRemovedClaim(claim.claimId, claim.ownerName), GREEN));
	}

	private void removeAllClaims(CommandSender sender, String[] args, String label) {
		if (args.length < 2) {
			sender.sendMessage(text(PaperText.usageRemoveAll(label), RED));
			return;
		}

		PlayerReference target = resolvePlayerReference(sender, args[1]);
		if (target == null) {
			return;
		}

		List<ClaimData> claims = this.runtime.services().claimStore().getClaimsForOwner(target.id());
		if (claims.isEmpty()) {
			sender.sendMessage(text(SafeZoneText.noOwnedClaims(target.name()), YELLOW));
			return;
		}

		if (args.length < 3 || !"confirm".equalsIgnoreCase(args[2])) {
			sender.sendMessage(text(PaperText.removeAllConfirm(label, args[1], claims.size()), RED));
			return;
		}

		SafeZoneConfig config = this.runtime.services().configService().current();
		for (ClaimData claim : claims) {
			if (this.runtime.services().claimStore().removeClaim(claim.claimId)) {
				this.runtime.services().notificationStore().queueClaimRemovedNotification(claim, sender.getName(), config);
			}
		}
		this.runtime.services().auditLogger().logAdminAction(
			sender.getName(),
			"REMOVEALL",
			"-",
			"owner=" + target.name() + " count=" + claims.size());
		sender.sendMessage(text(SafeZoneText.adminRemovedAll(target.name(), claims.size()), GREEN));
	}

	private void sendClaimDetails(CommandSender sender, ClaimData claim, String access) {
		claim.ensureDefaults();
		sender.sendMessage(text(SafeZoneText.claimTitle(claim.claimId), AQUA));
		sender.sendMessage(text(SafeZoneText.claimOwnerWithUuidLine(claim.ownerName, claim.ownerUuid), GRAY));
		sender.sendMessage(text(SafeZoneText.accessHereLine(access), GRAY));
		sender.sendMessage(text(SafeZoneText.claimSizeLine(claim.getWidth(), claim.getDepth()), GRAY));
		sender.sendMessage(text(SafeZoneText.claimBoundsLine(PaperCommandSupport.formatBounds(claim)), GRAY));
		sender.sendMessage(text(SafeZoneText.claimCenterLine(PaperCommandSupport.formatCenter(claim)), GRAY));
		sender.sendMessage(text(SafeZoneText.claimTrustedPlayersLine(PaperCommandSupport.formatTrustedSummary(claim)), GRAY));
		sender.sendMessage(text(SafeZoneText.claimCreatedLine(PaperCommandSupport.formatTimestamp(claim.createdAt)), GRAY));
		sender.sendMessage(text(SafeZoneText.claimLastActiveLine(PaperCommandSupport.formatTimestamp(claim.lastActiveAt)), YELLOW));
	}

	private void sendClaimList(CommandSender sender, List<ClaimData> claims, int page, String title, boolean includeOwner) {
		if (claims.isEmpty()) {
			sender.sendMessage(text(title, AQUA));
			sender.sendMessage(text(SafeZoneText.noClaimsFound(), YELLOW));
			return;
		}

		int totalPages = Math.max(1, (claims.size() + PaperCommandSupport.CLAIMS_PER_PAGE - 1) / PaperCommandSupport.CLAIMS_PER_PAGE);
		int clampedPage = Math.max(1, Math.min(page, totalPages));
		int startIndex = (clampedPage - 1) * PaperCommandSupport.CLAIMS_PER_PAGE;
		int endIndex = Math.min(startIndex + PaperCommandSupport.CLAIMS_PER_PAGE, claims.size());

		sender.sendMessage(text(title + " (page " + clampedPage + "/" + totalPages + ")", AQUA));
		for (int index = startIndex; index < endIndex; index++) {
			ClaimData claim = claims.get(index);
			String ownerPart = includeOwner ? claim.ownerName + " • " : "";
			sender.sendMessage(text(
				"%s%s • %dx%d • center %s"
					.formatted(ownerPart, claim.claimId, claim.getWidth(), claim.getDepth(), PaperCommandSupport.formatCenter(claim)),
				GRAY));
		}
	}

	private void reload(CommandSender sender) {
		this.runtime.reload();
		sender.sendMessage(text(PaperText.reloadSuccess(), GREEN));
		sendStatus(sender);
	}

	private ClaimData getClaim(CommandSender sender, String claimId) {
		Optional<ClaimData> claim = this.runtime.services().claimStore().getClaim(claimId);
		if (claim.isPresent()) {
			return claim.get();
		}

		sender.sendMessage(text(SafeZoneText.CLAIM_NOT_FOUND, RED));
		return null;
	}

	private PlayerReference resolvePlayerReference(CommandSender sender, String input) {
		Player onlinePlayer = Bukkit.getPlayerExact(input);
		if (onlinePlayer == null) {
			onlinePlayer = Bukkit.getOnlinePlayers().stream()
				.filter(player -> player.getName().equalsIgnoreCase(input))
				.findFirst()
				.orElse(null);
		}
		if (onlinePlayer != null) {
			return new PlayerReference(onlinePlayer.getUniqueId(), onlinePlayer.getName());
		}

		for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
			if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(input)) {
				return new PlayerReference(offlinePlayer.getUniqueId(), offlinePlayer.getName());
			}
		}

		try {
			UUID playerId = UUID.fromString(input);
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
			String playerName = offlinePlayer.getName();
			return new PlayerReference(playerId, playerName == null || playerName.isBlank() ? input : playerName);
		} catch (IllegalArgumentException ignored) {
			sender.sendMessage(text(PaperText.unknownPlayer(input), RED));
			return null;
		}
	}

	private List<String> playerSuggestions(String prefix) {
		LinkedHashSet<String> suggestions = new LinkedHashSet<>();
		Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
		for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
			if (offlinePlayer.getName() != null && !offlinePlayer.getName().isBlank()) {
				suggestions.add(offlinePlayer.getName());
			}
		}
		return filterByPrefix(new ArrayList<>(suggestions), prefix);
	}

	private static List<String> filterByPrefix(List<String> values, String prefix) {
		String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
		return values.stream()
			.filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
			.toList();
	}

	private Integer parsePage(CommandSender sender, String[] args, int argumentIndex) {
		if (args.length <= argumentIndex) {
			return 1;
		}
		return parsePositiveInteger(sender, args[argumentIndex], "Page");
	}

	private Integer parsePositiveInteger(CommandSender sender, String input, String label) {
		try {
			int value = Integer.parseInt(input);
			if (value < 1) {
				throw new NumberFormatException();
			}
			return value;
		} catch (NumberFormatException exception) {
			sender.sendMessage(text(SafeZoneText.positiveWholeNumber(label), RED));
			return null;
		}
	}

	private static Location resolveTeleportLocation(org.bukkit.World world, ClaimData claim) {
		var center = claim.getCenter();
		int highestY = world.getHighestBlockYAt(center.x(), center.z());
		return new Location(world, center.x() + 0.5D, highestY + 1.0D, center.z() + 0.5D);
	}

	private record PlayerReference(UUID id, String name) {
	}
}
