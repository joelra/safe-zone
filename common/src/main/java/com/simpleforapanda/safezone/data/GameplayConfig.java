package com.simpleforapanda.safezone.data;

import java.util.concurrent.TimeUnit;

public class GameplayConfig {
	public static final String DEFAULT_CLAIM_WAND_ITEM_ID = "minecraft:golden_hoe";
	public static final int DEFAULT_MAX_CLAIMS = 3;
	public static final int DEFAULT_MAX_CLAIM_WIDTH = 64;
	public static final int DEFAULT_MAX_CLAIM_DEPTH = 64;
	public static final int DEFAULT_MIN_CLAIM_DISTANCE = 10;
	public static final int DEFAULT_CLAIM_EXPIRY_DAYS = 0;
	public static final int DEFAULT_NOTIFICATION_RETENTION_DAYS = 30;
	public static final int DEFAULT_WAND_REMOVE_CONFIRM_SECONDS = 5;
	public static final int DEFAULT_COMMAND_REMOVE_CONFIRM_SECONDS = 10;
	public static final int DEFAULT_WAND_SELECTION_RANGE_BLOCKS = 64;
	public static final int DEFAULT_WAND_OUTLINE_STEP = 4;
	public static final int DEFAULT_WAND_CONFIRM_DISPLAY_SECONDS = 3;

	public String claimWandItemId = DEFAULT_CLAIM_WAND_ITEM_ID;
	public boolean starterKitEnabled = true;
	public boolean dropStarterKitWhenInventoryFull = true;
	public int defaultMaxClaims = DEFAULT_MAX_CLAIMS;
	public int maxClaimWidth = DEFAULT_MAX_CLAIM_WIDTH;
	public int maxClaimDepth = DEFAULT_MAX_CLAIM_DEPTH;
	public boolean claimGapEnforced = false;
	public int claimGapMinDistance = DEFAULT_MIN_CLAIM_DISTANCE;
	public int claimExpiryDays = DEFAULT_CLAIM_EXPIRY_DAYS;
	public boolean notificationsEnabled = true;
	public int notificationRetentionDays = DEFAULT_NOTIFICATION_RETENTION_DAYS;
	public int wandRemoveConfirmSeconds = DEFAULT_WAND_REMOVE_CONFIRM_SECONDS;
	public int commandRemoveConfirmSeconds = DEFAULT_COMMAND_REMOVE_CONFIRM_SECONDS;
	public int wandSelectionRangeBlocks = DEFAULT_WAND_SELECTION_RANGE_BLOCKS;
	public int wandOutlineStep = DEFAULT_WAND_OUTLINE_STEP;
	public int wandConfirmDisplaySeconds = DEFAULT_WAND_CONFIRM_DISPLAY_SECONDS;

	public void ensureDefaults() {
		if (this.claimWandItemId == null || this.claimWandItemId.isBlank()) {
			this.claimWandItemId = DEFAULT_CLAIM_WAND_ITEM_ID;
		}
		if (this.defaultMaxClaims < 0) {
			this.defaultMaxClaims = DEFAULT_MAX_CLAIMS;
		}
		if (this.maxClaimWidth < 1) {
			this.maxClaimWidth = DEFAULT_MAX_CLAIM_WIDTH;
		}
		if (this.maxClaimDepth < 1) {
			this.maxClaimDepth = DEFAULT_MAX_CLAIM_DEPTH;
		}
		if (this.claimGapMinDistance < 0) {
			this.claimGapMinDistance = DEFAULT_MIN_CLAIM_DISTANCE;
		}
		if (this.claimExpiryDays < 0) {
			this.claimExpiryDays = DEFAULT_CLAIM_EXPIRY_DAYS;
		}
		if (this.notificationRetentionDays < 1) {
			this.notificationRetentionDays = DEFAULT_NOTIFICATION_RETENTION_DAYS;
		}
		if (this.wandRemoveConfirmSeconds < 1) {
			this.wandRemoveConfirmSeconds = DEFAULT_WAND_REMOVE_CONFIRM_SECONDS;
		}
		if (this.commandRemoveConfirmSeconds < 1) {
			this.commandRemoveConfirmSeconds = DEFAULT_COMMAND_REMOVE_CONFIRM_SECONDS;
		}
		if (this.wandSelectionRangeBlocks < 1) {
			this.wandSelectionRangeBlocks = DEFAULT_WAND_SELECTION_RANGE_BLOCKS;
		}
		if (this.wandOutlineStep < 1) {
			this.wandOutlineStep = DEFAULT_WAND_OUTLINE_STEP;
		}
		if (this.wandConfirmDisplaySeconds < 0) {
			this.wandConfirmDisplaySeconds = DEFAULT_WAND_CONFIRM_DISPLAY_SECONDS;
		}
	}

	public int effectiveMinDistance() {
		return this.claimGapEnforced ? this.claimGapMinDistance : 0;
	}

	public long claimExpiryMillis() {
		if (this.claimExpiryDays <= 0) {
			return 0L;
		}
		return TimeUnit.DAYS.toMillis(this.claimExpiryDays);
	}

	public long notificationRetentionMillis() {
		return TimeUnit.DAYS.toMillis(this.notificationRetentionDays);
	}

	public long wandRemoveConfirmWindowMillis() {
		return TimeUnit.SECONDS.toMillis(this.wandRemoveConfirmSeconds);
	}

	public long commandRemoveConfirmWindowMillis() {
		return TimeUnit.SECONDS.toMillis(this.commandRemoveConfirmSeconds);
	}

	public long wandConfirmDisplayMillis() {
		return TimeUnit.SECONDS.toMillis(this.wandConfirmDisplaySeconds);
	}

	public GameplayConfig copy() {
		GameplayConfig copy = new GameplayConfig();
		copy.claimWandItemId = this.claimWandItemId;
		copy.starterKitEnabled = this.starterKitEnabled;
		copy.dropStarterKitWhenInventoryFull = this.dropStarterKitWhenInventoryFull;
		copy.defaultMaxClaims = this.defaultMaxClaims;
		copy.maxClaimWidth = this.maxClaimWidth;
		copy.maxClaimDepth = this.maxClaimDepth;
		copy.claimGapEnforced = this.claimGapEnforced;
		copy.claimGapMinDistance = this.claimGapMinDistance;
		copy.claimExpiryDays = this.claimExpiryDays;
		copy.notificationsEnabled = this.notificationsEnabled;
		copy.notificationRetentionDays = this.notificationRetentionDays;
		copy.wandRemoveConfirmSeconds = this.wandRemoveConfirmSeconds;
		copy.commandRemoveConfirmSeconds = this.commandRemoveConfirmSeconds;
		copy.wandSelectionRangeBlocks = this.wandSelectionRangeBlocks;
		copy.wandOutlineStep = this.wandOutlineStep;
		copy.wandConfirmDisplaySeconds = this.wandConfirmDisplaySeconds;
		return copy;
	}
}
