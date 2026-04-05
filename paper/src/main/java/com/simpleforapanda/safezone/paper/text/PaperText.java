package com.simpleforapanda.safezone.paper.text;

import java.util.List;

public final class PaperText {
	private PaperText() {
	}

	public static List<String> playerHelpLines(String label) {
		return List.of(
			"Safe Zone player commands",
			"/" + label + " status - show your claim summary and current location",
			"/" + label + " here - inspect the claim at your feet",
			"/" + label + " info [claimId] - inspect one of your accessible claims",
			"/" + label + " list [page] - list claims you own",
			"/" + label + " trusted [page] - list claims that trust you",
			"/" + label + " trust [claimId] - open the build access menu for your current or named claim",
			"/" + label + " remove <claimId> - remove one of your claims with confirmation");
	}

	public static List<String> adminHelpLines(String label) {
		return List.of(
			"Safe Zone admin commands",
			"/" + label + " status - show Paper runtime and claim storage status",
			"/" + label + " list [page] - browse all loaded claims",
			"/" + label + " list owner|trusted <player> [page] - filter claims by player",
			"/" + label + " info [claimId] - inspect any loaded claim",
			"/" + label + " tp <claimId> - teleport to the center of a claim",
			"/" + label + " inspect [claimId] - toggle in-world inspect or inspect one claim",
			"/" + label + " notifications [purge confirm] - review or clear queued admin notifications",
			"/" + label + " givewand [player] - issue a claim wand",
			"/" + label + " limits <player> [maxClaims|clear] - manage claim limit overrides",
			"/" + label + " transfer|trust|untrust <claimId> <player> - manage claim access",
			"/" + label + " remove <claimId> - delete one claim",
			"/" + label + " removeall <player> confirm - delete all claims for one owner",
			"/" + label + " reload - reload config, claims, and notifications");
	}

	public static String paperPlayerStatusTitle() {
		return "Safe Zone Paper status";
	}

	public static String playerViewsInGameHint() {
		return "Player-specific views are available in game with /claim here, /claim list, and /claim trusted.";
	}

	public static String playerClaimHereMissing() {
		return "You are not standing in a Safe Zone claim.";
	}

	public static String trustClaimFromCurrentAreaHint() {
		return "Stand inside one of your claims or pass a claim id.";
	}

	public static String trustOwnerOrAdminOnlyThere() {
		return "Only the owner or an admin can change build access here.";
	}

	public static String trustOwnerOrAdminOnlyThatClaim() {
		return "Only the owner or an admin can change build access on that claim.";
	}

	public static String usageClaimRemove() {
		return "Usage: /claim remove <claimId>";
	}

	public static String adminRuntimeStatusTitle() {
		return "Safe Zone Paper runtime status";
	}

	public static String usageListFilter() {
		return "Usage: /sz list owner|trusted <player> [page]";
	}

	public static String usageInspect() {
		return "Usage: /sz inspect <claimId>";
	}

	public static String playerOnlyTp() {
		return "Only players can use /sz tp.";
	}

	public static String usageTp() {
		return "Usage: /sz tp <claimId>";
	}

	public static String noMainOverworld() {
		return "No main Overworld is available for Safe Zone claims.";
	}

	public static String notificationsUsage(String label) {
		return "Usage: /" + label + " notifications [purge confirm]";
	}

	public static String consoleClaimInfoHint() {
		return "Use /sz info <claimId> from console, or run /sz here in game.";
	}

	public static String targetPlayerMustBeOnline() {
		return "Target player must be online for /sz givewand.";
	}

	public static String usageGiveWand() {
		return "Usage: /sz givewand <onlinePlayer>";
	}

	public static String adminGiftClaimWandNotice() {
		return "An admin gave you a Safe Zone claim wand.";
	}

	public static String usageLimits() {
		return "Usage: /sz limits <player> [maxClaims|clear]";
	}

	public static String claimLimitsTitle(String playerName) {
		return "Claim limits for " + playerName;
	}

	public static String ownedClaimsLine(int ownedClaims) {
		return "Owned claims: " + ownedClaims;
	}

	public static String defaultLimitLine(int defaultMaxClaims) {
		return "Default limit: " + defaultMaxClaims;
	}

	public static String overrideLine(String overrideValue) {
		return "Override: " + overrideValue;
	}

	public static String effectiveLimitLine(int effectiveLimit) {
		return "Effective limit: " + effectiveLimit;
	}

	public static String clearedLimitOverride(String playerName) {
		return "Cleared the claim limit override for " + playerName + ".";
	}

	public static String usageTransfer() {
		return "Usage: /sz transfer <claimId> <player>";
	}

	public static String alreadyOwnsClaim(String playerName, String claimId) {
		return playerName + " already owns claim " + claimId + ".";
	}

	public static String transferredClaimFromAdmin(String claimId, String playerName) {
		return "An admin transferred claim " + claimId + " to " + playerName + ".";
	}

	public static String transferredClaimToYou(String claimId) {
		return "An admin transferred claim " + claimId + " to you.";
	}

	public static String usageTrustCommand(boolean trusted) {
		return "Usage: /sz " + (trusted ? "trust" : "untrust") + " <claimId> <player>";
	}

	public static String adminTrustChanged(boolean trusted, String playerName, String claimId) {
		return (trusted ? "Added " : "Removed ") + playerName + (trusted ? " to " : " from ") + "claim " + claimId + ".";
	}

	public static String adminTrustAlreadyState(boolean trusted, String playerName, String claimId) {
		return playerName + " was already " + (trusted ? "trusted" : "not trusted") + " on claim " + claimId + ".";
	}

	public static String usageAdminRemove() {
		return "Usage: /sz remove <claimId>";
	}

	public static String usageRemoveAll(String label) {
		return "Usage: /" + label + " removeall <player> confirm";
	}

	public static String removeAllConfirm(String label, String playerName, int claimCount) {
		return "Confirm removal of " + claimCount + " claims with /" + label + " removeall " + playerName + " confirm";
	}

	public static String reloadSuccess() {
		return "Safe Zone Paper data reloaded.";
	}

	public static String unknownPlayer(String input) {
		return "Unknown player: " + input + ". Use an online player, a cached offline name, or a UUID.";
	}
}
