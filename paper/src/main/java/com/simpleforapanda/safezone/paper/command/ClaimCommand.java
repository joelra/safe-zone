package com.simpleforapanda.safezone.paper.command;

import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.SafeZoneConfig;
import com.simpleforapanda.safezone.paper.runtime.PaperClaimStore;
import com.simpleforapanda.safezone.paper.runtime.PaperRuntime;
import com.simpleforapanda.safezone.paper.text.PaperText;
import com.simpleforapanda.safezone.text.SafeZoneText;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

public final class ClaimCommand implements CommandExecutor, TabCompleter {
	private static final List<String> SUBCOMMANDS = List.of("help", "status", "here", "info", "list", "trusted", "trust", "remove", "show");
	private static final Map<UUID, PendingClaimRemoval> PENDING_REMOVALS = new ConcurrentHashMap<>();

	private final PaperRuntime runtime;

	public ClaimCommand(PaperRuntime runtime) {
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
			case "here" -> sendClaimAtCurrentPosition(sender);
			case "info" -> sendClaimInfo(sender, args);
			case "list" -> sendOwnedClaims(sender, parsePage(sender, args, 1));
			case "trusted" -> sendTrustedClaims(sender, parsePage(sender, args, 1));
			case "trust" -> openTrustMenu(sender, args);
			case "remove" -> removeOwnedClaim(sender, args);
			case "show" -> toggleClaimShow(sender);
			default -> sender.sendMessage(text(SafeZoneText.unknownSubcommand(label), RED));
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			String prefix = args[0].toLowerCase(Locale.ROOT);
			return SUBCOMMANDS.stream().filter(subcommand -> subcommand.startsWith(prefix)).toList();
		}
		if (args.length == 2 && ("info".equalsIgnoreCase(args[0]) || "remove".equalsIgnoreCase(args[0]) || "trust".equalsIgnoreCase(args[0]))) {
			String prefix = args[1].toLowerCase(Locale.ROOT);
			List<String> claimIds = switch (args[0].toLowerCase(Locale.ROOT)) {
				case "remove" -> getOwnedClaimIds(sender);
				case "trust" -> getManageableClaimIds(sender);
				default -> getAccessibleClaimIds(sender);
			};
			return claimIds.stream()
				.filter(claimId -> claimId.toLowerCase(Locale.ROOT).startsWith(prefix))
				.toList();
		}
		return List.of();
	}

	private void sendHelp(CommandSender sender, String label) {
		var lines = PaperText.playerHelpLines(label);
		sender.sendMessage(text(lines.getFirst(), AQUA));
		for (int index = 1; index < lines.size(); index++) {
			sender.sendMessage(text(lines.get(index), GRAY));
		}
	}

	private void sendStatus(CommandSender sender) {
		SafeZoneConfig config = this.runtime.services().configService().current();
		PaperClaimStore claimStore = this.runtime.services().claimStore();
		sender.sendMessage(text(PaperText.paperPlayerStatusTitle(), AQUA));
		sender.sendMessage(text(
			"Claims loaded: %d across %d owners".formatted(claimStore.countClaims(), this.runtime.status().ownerCount()),
			GRAY));
		sender.sendMessage(text(
			"Defaults: max=%d, size=%dx%d, gap=%d, expiry=%dd"
				.formatted(
					config.gameplay.defaultMaxClaims,
					config.gameplay.maxClaimWidth,
					config.gameplay.maxClaimDepth,
					config.gameplay.effectiveMinDistance(),
					config.gameplay.claimExpiryDays),
			GRAY));
		if (!(sender instanceof Player player)) {
			sender.sendMessage(text(PaperText.playerViewsInGameHint(), YELLOW));
			return;
		}

		int ownedClaims = claimStore.getClaimsForOwner(player.getUniqueId()).size();
		int trustedClaims = claimStore.getClaimsTrustedFor(player.getUniqueId()).size();
		int maxClaims = claimStore.getMaxClaims(player.getUniqueId(), config.gameplay.defaultMaxClaims);
		String starterKitState = !config.gameplay.starterKitEnabled
			? "disabled"
			: claimStore.hasReceivedStarterKit(player.getUniqueId()) ? "received" : "available";
		Optional<ClaimData> currentClaim = claimStore.getClaimAt(player.getLocation());

		sender.sendMessage(text(SafeZoneText.playerOwnedSummary(ownedClaims, maxClaims, trustedClaims), GREEN));
		sender.sendMessage(text(SafeZoneText.starterKitStateLine(starterKitState), GRAY));
		sender.sendMessage(text(
			SafeZoneText.currentLocationLine(currentClaim
				.map(claim -> claim.claimId + " (" + PaperCommandSupport.describeAccess(claim, player, sender.hasPermission("safezone.command.admin")) + ")")
				.orElse("unclaimed")),
			GRAY));
	}

	private void sendClaimAtCurrentPosition(CommandSender sender) {
		Player player = requirePlayer(sender);
		if (player == null) {
			return;
		}

		this.runtime.services().claimStore().getClaimAt(player.getLocation())
			.ifPresentOrElse(
				claim -> sendClaimDetails(sender, claim, PaperCommandSupport.describeAccess(claim, player, sender.hasPermission("safezone.command.admin"))),
				() -> sender.sendMessage(text(PaperText.playerClaimHereMissing(), RED)));
	}

	private void sendClaimInfo(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sendClaimAtCurrentPosition(sender);
			return;
		}

		PaperClaimStore claimStore = this.runtime.services().claimStore();
		String claimId = args[1];
		Optional<ClaimData> claim = claimStore.getClaim(claimId);
		if (claim.isEmpty()) {
			sender.sendMessage(text(SafeZoneText.CLAIM_NOT_FOUND, RED));
			return;
		}

		if (sender instanceof Player player && !canAccessClaim(player, claim.get())) {
			sender.sendMessage(text(SafeZoneText.NO_CLAIM_ACCESS, RED));
			return;
		}

		String access = sender instanceof Player player
			? PaperCommandSupport.describeAccess(claim.get(), player, sender.hasPermission("safezone.command.admin"))
			: "read-only";
		sendClaimDetails(sender, claim.get(), access);
	}

	private void sendOwnedClaims(CommandSender sender, Integer page) {
		if (page == null) {
			return;
		}

		Player player = requirePlayer(sender);
		if (player == null) {
			return;
		}

		sendClaimList(sender, this.runtime.services().claimStore().getClaimsForOwner(player.getUniqueId()), page, "Claims you own", false);
	}

	private void removeOwnedClaim(CommandSender sender, String[] args) {
		Player player = requirePlayer(sender);
		if (player == null) {
			return;
		}
		if (args.length < 2) {
			sender.sendMessage(text(PaperText.usageClaimRemove(), RED));
			return;
		}

		PaperClaimStore claimStore = this.runtime.services().claimStore();
		String claimId = args[1];
		Optional<ClaimData> claim = claimStore.getClaim(claimId);
		if (claim.isEmpty()) {
			sender.sendMessage(text(SafeZoneText.CLAIM_NOT_FOUND, RED));
			return;
		}
		if (!claim.get().owns(player.getUniqueId())) {
			sender.sendMessage(text(SafeZoneText.OWN_CLAIMS_ONLY, RED));
			return;
		}

		long now = System.currentTimeMillis();
		PendingClaimRemoval pendingRemoval = PENDING_REMOVALS.get(player.getUniqueId());
		int confirmSeconds = this.runtime.services().configService().current().gameplay.commandRemoveConfirmSeconds;
		if (pendingRemoval == null || !pendingRemoval.matches(claimId, now)) {
			PENDING_REMOVALS.put(
				player.getUniqueId(),
				new PendingClaimRemoval(claimId, now + this.runtime.services().configService().current().gameplay.commandRemoveConfirmWindowMillis()));
			sender.sendMessage(text(
				"Run /claim remove " + claimId + " again within " + confirmSeconds + " seconds to confirm.",
				RED));
			return;
		}

		PENDING_REMOVALS.remove(player.getUniqueId());
		if (!claimStore.removeClaim(claimId)) {
			sender.sendMessage(text(SafeZoneText.claimAlreadyRemoved(), RED));
			return;
		}

		sender.sendMessage(text(SafeZoneText.playerRemovedClaim(claimId), GREEN));
	}

	private void toggleClaimShow(CommandSender sender) {
		Player player = requirePlayer(sender);
		if (player == null) {
			return;
		}
		boolean enabled = this.runtime.services().claimStore().toggleClaimShow(player.getUniqueId());
		player.sendMessage(text("Claim outlines: ", GRAY)
			.append(text(enabled ? "ON" : "OFF", enabled ? GREEN : RED)));
	}

	private void sendTrustedClaims(CommandSender sender, Integer page) {
		if (page == null) {
			return;
		}

		Player player = requirePlayer(sender);
		if (player == null) {
			return;
		}

		sendClaimList(sender, this.runtime.services().claimStore().getClaimsTrustedFor(player.getUniqueId()), page, "Claims that trust you", true);
	}

	private void openTrustMenu(CommandSender sender, String[] args) {
		Player player = requirePlayer(sender);
		if (player == null) {
			return;
		}

		Optional<ClaimData> claim = resolveManageableClaim(player, args);
		if (claim.isEmpty()) {
			return;
		}

		this.runtime.services().trustMenuService().open(player, claim.get().claimId);
	}

	private void sendClaimDetails(CommandSender sender, ClaimData claim, String access) {
		claim.ensureDefaults();
		sender.sendMessage(text(SafeZoneText.claimTitle(claim.claimId), AQUA));
		sender.sendMessage(text(SafeZoneText.claimOwnerLine(claim.ownerName), GRAY));
		sender.sendMessage(text(SafeZoneText.yourAccessLine(access), GRAY));
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

	private boolean canAccessClaim(Player player, ClaimData claim) {
		return claim.owns(player.getUniqueId()) || claim.isTrusted(player.getUniqueId()) || player.hasPermission("safezone.command.admin");
	}

	private boolean canManageTrust(Player player, ClaimData claim) {
		return claim.owns(player.getUniqueId()) || player.hasPermission("safezone.command.admin");
	}

	private List<String> getAccessibleClaimIds(CommandSender sender) {
		PaperClaimStore claimStore = this.runtime.services().claimStore();
		if (!(sender instanceof Player player)) {
			return claimStore.getClaimIds();
		}

		LinkedHashSet<String> claimIds = new LinkedHashSet<>();
		claimStore.getClaimsForOwner(player.getUniqueId()).forEach(claim -> claimIds.add(claim.claimId));
		claimStore.getClaimsTrustedFor(player.getUniqueId()).forEach(claim -> claimIds.add(claim.claimId));
		if (player.hasPermission("safezone.command.admin")) {
			claimIds.addAll(claimStore.getClaimIds());
		}
		return List.copyOf(claimIds);
	}

	private List<String> getOwnedClaimIds(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			return List.of();
		}
		return this.runtime.services().claimStore().getClaimsForOwner(player.getUniqueId()).stream()
			.map(claim -> claim.claimId)
			.toList();
	}

	private List<String> getManageableClaimIds(CommandSender sender) {
		PaperClaimStore claimStore = this.runtime.services().claimStore();
		if (!(sender instanceof Player player)) {
			return claimStore.getClaimIds();
		}

		LinkedHashSet<String> claimIds = new LinkedHashSet<>();
		claimStore.getClaimsForOwner(player.getUniqueId()).forEach(claim -> claimIds.add(claim.claimId));
		if (player.hasPermission("safezone.command.admin")) {
			claimIds.addAll(claimStore.getClaimIds());
		}
		return List.copyOf(claimIds);
	}

	private Optional<ClaimData> resolveManageableClaim(Player player, String[] args) {
		PaperClaimStore claimStore = this.runtime.services().claimStore();
		if (args.length < 2) {
			Optional<ClaimData> currentClaim = claimStore.getClaimAt(player.getLocation());
			if (currentClaim.isEmpty()) {
				player.sendMessage(text(PaperText.trustClaimFromCurrentAreaHint(), RED));
				return Optional.empty();
			}
			if (!canManageTrust(player, currentClaim.get())) {
				player.sendMessage(text(PaperText.trustOwnerOrAdminOnlyThere(), RED));
				return Optional.empty();
			}
			return currentClaim;
		}

		Optional<ClaimData> claim = claimStore.getClaim(args[1]);
		if (claim.isEmpty()) {
			player.sendMessage(text(SafeZoneText.CLAIM_NOT_FOUND, RED));
			return Optional.empty();
		}
		if (!canManageTrust(player, claim.get())) {
			player.sendMessage(text(PaperText.trustOwnerOrAdminOnlyThatClaim(), RED));
			return Optional.empty();
		}
		return claim;
	}

	private Player requirePlayer(CommandSender sender) {
		if (sender instanceof Player player) {
			return player;
		}
		sender.sendMessage(text(SafeZoneText.playerOnlySubcommand(), RED));
		return null;
	}

	private Integer parsePage(CommandSender sender, String[] args, int argumentIndex) {
		if (args.length <= argumentIndex) {
			return 1;
		}
		try {
			int page = Integer.parseInt(args[argumentIndex]);
			if (page < 1) {
				throw new NumberFormatException();
			}
			return page;
		} catch (NumberFormatException exception) {
			sender.sendMessage(text(SafeZoneText.positiveWholeNumber("Page"), RED));
			return null;
		}
	}

	private record PendingClaimRemoval(String claimId, long expiresAt) {
		private boolean matches(String claimId, long now) {
			return this.claimId.equals(claimId) && now <= this.expiresAt;
		}
	}
}
