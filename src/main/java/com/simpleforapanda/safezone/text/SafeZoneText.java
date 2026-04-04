package com.simpleforapanda.safezone.text;

public final class SafeZoneText {
	public static final String SAFE_ZONE_PREFIX = "[Safe Zone] ";
	public static final String STEP_LABEL = "Next";
	public static final String TRUST_MENU_TITLE = "Safe Zone • Build Access";
	public static final String CLAIM_NOT_FOUND = "That safe zone was not found.";
	public static final String PROFILE_NOT_FOUND = "That player profile was not found.";
	public static final String PLAYER_HELP_TITLE = "Safe Zone Player Commands";
	public static final String PLAYER_HELP_SUBTITLE = "Buttons below run or fill commands for your own safe zones.";
	public static final String PLAYER_HELP_TIP = "Tip: use Tab to autocomplete claim IDs in chat.";
	public static final String ADMIN_HELP_TITLE = "Safe Zone Admin Commands";
	public static final String ADMIN_HELP_SUBTITLE = "Staff tools for reviewing, moving, restoring, and removing safe zones.";
	public static final String ADMIN_HELP_TIP = "Tip: use Tab to autocomplete claim IDs and player profiles.";
	public static final String OWNED_CLAIMS_TITLE = "Your Claims";
	public static final String OWNED_CLAIMS_SUBTITLE = "Click a button or use page controls to manage your safe zones.";
	public static final String TRUSTED_CLAIMS_TITLE = "Trusted Claims";
	public static final String TRUSTED_CLAIMS_SUBTITLE = "These safe zones trust you to build.";
	public static final String NO_CLAIM_HERE = "Stand inside a safe zone and try again.";
	public static final String OWN_CLAIMS_ONLY = "You can only manage your own safe zones.";
	public static final String NO_CLAIM_ACCESS = "You do not have access to that safe zone.";
	public static final String TRUST_OWNER_ONLY = "Only the owner can change who can build here.";
	public static final String TRUST_MENU_INFO_TITLE = "Build Access";
	public static final String TRUST_MENU_INFO_HINT = "Click a head to allow or remove building.";
	public static final String TRUST_MENU_EMPTY = "No players to show right now.";
	public static final String NO_SAFE_ZONE_HERE = "There is no safe zone at your current position.";
	public static final String NO_SAFE_ZONE_AT_SPOT = "No safe zone at this spot.";
	public static final String BLOCKED_BUILD_HINT = "You cannot build here yet. Ask the owner to trust you first.";
	public static final String CLAIM_ACTION_CANCELLED = "Selection cancelled.";
	public static final String CLAIM_RESIZE_CANCELLED = "Resize cancelled.";
	public static final String CLAIM_STEP_RESTART = "Start again by right-clicking the first corner.";
	public static final String CLAIM_STEP_RETRY_RESIZE = "Right-click a claim corner again if you want to resize it.";
	public static final String RESIZE_READY_TITLE = "Resize ready";
	public static final String RESIZE_READY_SUBTITLE = "Right-click the new corner";
	public static final String CORNER_SAVED_TITLE = "Corner 1 saved";
	public static final String CORNER_SAVED_SUBTITLE = "Right-click corner 2";
	public static final String STARTER_KIT_READY_TITLE = "Claim wand ready";
	public static final String REMOVE_AREA_TITLE = "Remove this area?";
	public static final String REMOVE_AREA_SUBTITLE = "Shift+right-click again";
	public static final String INSPECT_EMPTY_HINT = "Inspect mode is off.";
	public static final String INSPECT_ENABLED_HINT = "Hold Shift and right-click with an empty hand to inspect safe zones.";
	public static final String RELOAD_SUCCESS = "Reloaded Safe Zone data and ops settings from disk.";
	public static final String RELOAD_FAILURE = "Reload failed. Check server logs; existing in-memory data was left unchanged.";
	public static final String LIST_NO_MATCHES = "No matching safe zones found.";
	public static final String CLICK_ACTIONS_HINT = "Click an action or use the page controls below.";
	public static final String TRUSTED_NONE = "none";
	public static final String ACTIONS_LABEL = "Actions";

	private SafeZoneText() {
	}

	public static String claimTitle(String claimId) {
		return "Claim " + claimId;
	}

	public static String playerRemoveConfirm(String claimId, int seconds) {
		return "Run /claim remove " + claimId + " again within " + seconds + " seconds to remove this safe zone.";
	}

	public static String playerRemovedClaim(String claimId) {
		return "Removed safe zone " + claimId + ".";
	}

	public static String adminRemovedClaim(String claimId, String ownerName) {
		return "Removed " + claimId + " owned by " + ownerName + ".";
	}

	public static String adminRemoveAllConfirm(String playerName, int claimCount, int seconds) {
		return "Run /sz removeall " + playerName + " again within " + seconds + " seconds to remove " + claimCount + " safe zones.";
	}

	public static String adminRemovedAll(String playerName, int claimCount) {
		return "Removed " + claimCount + " safe zones owned by " + playerName + ".";
	}

	public static String adminNotificationStatus(boolean notificationsEnabled, int pendingCount) {
		return "Offline admin notifications are " + (notificationsEnabled ? "enabled" : "disabled")
			+ ". Pending notices: " + pendingCount + ".";
	}

	public static String adminNotificationPurgeHint(int pendingCount) {
		return "Clear " + pendingCount + " pending offline admin notices from notifications.json.";
	}

	public static String adminNotificationPurgeConfirm(int pendingCount) {
		return "Run /sz notifications purge confirm to delete " + pendingCount
			+ " pending offline admin notices from notifications.json.";
	}

	public static String adminNotificationPurgeEmpty() {
		return "There are no pending offline admin notices to purge.";
	}

	public static String adminNotificationPurged(int purgedCount) {
		return purgedCount > 0
			? "Purged " + purgedCount + " pending offline admin notices from notifications.json."
			: adminNotificationPurgeEmpty();
	}

	public static String adminTransferSuccess(String claimId, String playerName) {
		return "Moved " + claimId + " to " + playerName + ".";
	}

	public static String adminTrustStatus(boolean trusted, String playerName, String claimId) {
		return playerName + (trusted ? " now can build in " : " can no longer build in ") + claimId + ".";
	}

	public static String adminTeleportSuccess(String claimId) {
		return "Moved to safe zone " + claimId + ".";
	}

	public static String adminGiveWandSuccess(String wandName, String playerName) {
		return "Given a " + wandName + " claim wand to " + playerName + ".";
	}

	public static String adminLimitSuccess(String playerName, int maxClaims) {
		return "Set " + playerName + "'s safe zone limit to " + maxClaims + ".";
	}

	public static String adminInspectSummary(String claimId, String ownerName, int width, int depth, int trustedCount) {
		return claimId + " • owner " + ownerName + " • size " + width + "x" + depth + " • trusted " + trustedCount;
	}

	public static String adminClaimListTitle(String playerName) {
		return "Claims owned by " + playerName;
	}

	public static String adminTrustedListTitle(String playerName) {
		return "Claims trusting " + playerName;
	}

	public static String noOwnedClaims(String playerName) {
		return playerName + " does not own any safe zones.";
	}

	public static String ownerAlreadyHasBuildAccess() {
		return "The owner already can build there.";
	}

	public static String trustMenuToggleStatus(boolean trusted, String playerName, String claimId) {
		return playerName + (trusted ? " can build in " + claimId + " now." : " can no longer build in " + claimId + ".");
	}

	public static String trustMenuClaimLine(String claimId) {
		return "Claim: " + claimId;
	}

	public static String trustMenuPageLine(int currentPage, int totalPages) {
		return "Page " + currentPage + " / " + totalPages;
	}

	public static String trustMenuPlayerCountLine(int totalEntries) {
		return totalEntries == 0 ? TRUST_MENU_EMPTY : totalEntries + " players shown";
	}

	public static String trustMenuTrustedSummary(long trustedCount, long offlineTrustedCount) {
		return trustedCount + " trusted, " + offlineTrustedCount + " offline";
	}

	public static String trustMenuPlayerStatus(boolean trusted) {
		return trusted ? "Status: can build here" : "Status: cannot build here";
	}

	public static String trustMenuPlayerAction(boolean trusted) {
		return trusted ? "Click: remove build access" : "Click: let them build here";
	}

	public static String trustMenuSeenStatus(boolean online) {
		return online ? "Seen: online now" : "Seen: offline player";
	}

	public static String newAreaLabel() {
		return "New area ";
	}

	public static String resizeLabel() {
		return "Resize ";
	}

	public static String finishSelection() {
		return "Right-click to finish";
	}

	public static String cancelSelection() {
		return "Shift + right-click to cancel";
	}

	public static String removingLabel() {
		return "Removing ";
	}

	public static String removeConfirmStep() {
		return "Shift + right-click again to confirm";
	}

	public static String ownerClaimPrefix() {
		return "Your ";
	}

	public static String ownerResizeHint() {
		return "Right-click a corner to resize";
	}

	public static String ownerRemoveHint() {
		return "Shift + right-click inside to remove";
	}

	public static String trustedCanBuild() {
		return "You can build here";
	}

	public static String adminViewPrefix() {
		return "Admin ";
	}

	public static String adminViewLabel() {
		return "view";
	}

	public static String deniedBuildHint() {
		return "You cannot build here";
	}

	public static String validationOverworldOnly() {
		return "Overworld only";
	}

	public static String validationClaimLimitReached() {
		return "Claim limit reached";
	}

	public static String validationTooBig(int maxClaimWidth, int maxClaimDepth) {
		return "Too big (max " + maxClaimWidth + "x" + maxClaimDepth + ")";
	}

	public static String validationTooCloseTo(String claimName) {
		return "Too close to " + claimName;
	}

	public static String genericConflictName() {
		return "another area";
	}

	public static String wandForeignClaimDenied() {
		return "That spot is inside someone else's safe zone. Ask the owner to trust you first.";
	}

	public static String wandResized(String claimSummary) {
		return "Updated " + claimSummary + ".";
	}

	public static String wandResizePickedUp(String claimSummary) {
		return "Picked up " + claimSummary + " for resizing.";
	}

	public static String wandResizeStep(String wandName) {
		return "Right-click the new corner with the " + wandName + ".";
	}

	public static String wandCornerSaved() {
		return "Saved the first corner.";
	}

	public static String wandCornerStep(String wandName) {
		return "Right-click the opposite corner with the " + wandName + ".";
	}

	public static String wandClaimed(String claimSummary) {
		return "Made " + claimSummary + ".";
	}

	public static String wandShareStep(String wandName) {
		return "Stand inside it and hold Shift + left-click with the " + wandName + " to share build access.";
	}

	public static String wandRemoved(String claimSummary) {
		return "Removed " + claimSummary + ".";
	}

	public static String wandRemovePrompt(String claimSummary) {
		return "Remove " + claimSummary + "?";
	}

	public static String wandRemoveConfirmStep(int seconds) {
		return "Hold Shift and right-click the same safe zone again within " + seconds + " seconds to confirm.";
	}

	public static String wandPutAwayCancelled(String wandName) {
		return "Claim action cancelled because you put the " + wandName + " away.";
	}

	public static String validationOverworldSentence() {
		return "Safe zones only work in the Overworld.";
	}

	public static String validationClaimLimitSentence(int maxClaims) {
		return "You already have " + maxClaims + " safe zones. Remove one before making another.";
	}

	public static String validationTooBigSentence(int maxClaimWidth, int maxClaimDepth) {
		return "That area is too big. Safe zones can be up to " + maxClaimWidth + "x" + maxClaimDepth + " blocks.";
	}

	public static String validationTooCloseSentence(int minDistance) {
		return "That area is too close to another safe zone. Leave at least " + minDistance + " blocks between them.";
	}

	public static String validationTooCloseSentence(String ownerName, String claimId) {
		return "That area is too close to " + ownerName + "'s safe zone (" + claimId + ").";
	}

	public static String starterKitInventoryFull(String wandName) {
		return "Your inventory is full. Make space and rejoin to receive your " + wandName + ".";
	}

	public static String starterKitHoldSubtitle(String wandName) {
		return "Hold the " + wandName;
	}

	public static String starterKitReady(String wandName) {
		return "Your " + wandName + " is your claim wand.";
	}

	public static String starterKitDropped() {
		return "Your inventory was full, so the wand dropped at your feet.";
	}

	public static String starterKitStepOne(String wandName) {
		return "Hold the " + wandName + " and right-click the first corner of your safe zone.";
	}

	public static String starterKitStepTwo() {
		return "Then right-click the opposite corner to finish the safe zone.";
	}

	public static String adminClaimInfoActions() {
		return "Teleport or fill a remove command.";
	}
}
