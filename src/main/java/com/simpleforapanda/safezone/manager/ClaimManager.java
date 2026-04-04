package com.simpleforapanda.safezone.manager;

import com.google.gson.reflect.TypeToken;
import com.simpleforapanda.safezone.SafeZone;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.data.PermissionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ClaimManager {
	private static final Type CLAIM_LIST_TYPE = new TypeToken<List<ClaimData>>() { }.getType();
	private static final Type PLAYER_LIMITS_TYPE = new TypeToken<Map<String, Integer>>() { }.getType();
	private static final Type STARTER_KIT_RECIPIENTS_TYPE = new TypeToken<List<String>>() { }.getType();
	private static final String CLAIMS_FILE_NAME = "claims.json";
	private static final String PLAYER_LIMITS_FILE_NAME = "player_limits.json";
	private static final String STARTER_KIT_RECIPIENTS_FILE_NAME = "starter_kit_recipients.json";
	private static final ClaimManager INSTANCE = new ClaimManager();

	private final ClaimValidator claimValidator;
	private final ClaimIdGenerator claimIdGenerator;
	private final ClaimLookupIndex claimLookupIndex = new ClaimLookupIndex();
	private final Map<String, ClaimData> claimsById = new LinkedHashMap<>();
	private final Map<String, Integer> playerLimits = new HashMap<>();
	private final List<String> starterKitRecipients = new ArrayList<>();
	private GameplayConfig gameplayConfig = new GameplayConfig();

	private MinecraftServer server;
	private Path dataDirectory;
	private Path claimsFilePath;
	private Path playerLimitsFilePath;
	private Path starterKitRecipientsFilePath;

	private ClaimManager() {
		this(new ClaimValidator(), new ClaimIdGenerator());
	}

	ClaimManager(ClaimValidator claimValidator, ClaimIdGenerator claimIdGenerator) {
		this.claimValidator = Objects.requireNonNull(claimValidator, "claimValidator");
		this.claimIdGenerator = Objects.requireNonNull(claimIdGenerator, "claimIdGenerator");
	}

	public static ClaimManager getInstance() {
		return INSTANCE;
	}

	public synchronized void load(MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		Path dataDirectory = server.getWorldPath(LevelResource.ROOT).resolve(SafeZone.MOD_ID);
		Path claimsFilePath = dataDirectory.resolve(CLAIMS_FILE_NAME);
		Path playerLimitsFilePath = dataDirectory.resolve(PLAYER_LIMITS_FILE_NAME);
		Path starterKitRecipientsFilePath = dataDirectory.resolve(STARTER_KIT_RECIPIENTS_FILE_NAME);
		boolean recoverFromBackup = ConfigManager.getInstance().getOpsSettings().recoverFromBackupOnLoadFailure;

		PersistentStateHelper.createDataDirectory(dataDirectory);

		List<ClaimData> loadedClaims = readJson(claimsFilePath, CLAIM_LIST_TYPE, List.<ClaimData>of(), "Safe Zone claims", recoverFromBackup);
		LinkedHashMap<String, ClaimData> loadedClaimsById = new LinkedHashMap<>();
		for (ClaimData claim : loadedClaims) {
			claim.ensureDefaults();
			loadedClaimsById.put(claim.claimId, claim);
		}

		Map<String, Integer> loadedPlayerLimits = new HashMap<>(readJson(playerLimitsFilePath, PLAYER_LIMITS_TYPE,
			Map.of(), "Safe Zone player limits", recoverFromBackup));
		loadedPlayerLimits.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < 1);
		List<String> loadedStarterKitRecipients = new ArrayList<>(readJson(starterKitRecipientsFilePath,
			STARTER_KIT_RECIPIENTS_TYPE, List.of(), "Safe Zone starter kit records", recoverFromBackup));
		GameplayConfig loadedGameplayConfig = ConfigManager.getInstance().getGameplayConfig();

		this.server = server;
		this.dataDirectory = dataDirectory;
		this.claimsFilePath = claimsFilePath;
		this.playerLimitsFilePath = playerLimitsFilePath;
		this.starterKitRecipientsFilePath = starterKitRecipientsFilePath;
		this.claimsById.clear();
		this.claimsById.putAll(loadedClaimsById);
		this.claimLookupIndex.rebuild(this.claimsById.values());
		this.playerLimits.clear();
		this.playerLimits.putAll(loadedPlayerLimits);
		this.starterKitRecipients.clear();
		this.starterKitRecipients.addAll(loadedStarterKitRecipients);
		this.gameplayConfig = loadedGameplayConfig;
		SafeZone.LOGGER.info("Loaded {} Safe Zone claims, {} player limit overrides, {} starter kit records, wand={}, default max claims={}, max size={}x{}, gap enforced={}, min gap={}, notifications enabled={}, notification retention={}d",
			this.claimsById.size(),
			this.playerLimits.size(),
			this.starterKitRecipients.size(),
			this.gameplayConfig.claimWandItemId,
			this.gameplayConfig.defaultMaxClaims,
			this.gameplayConfig.maxClaimWidth,
			this.gameplayConfig.maxClaimDepth,
			this.gameplayConfig.claimGapEnforced,
			this.gameplayConfig.effectiveMinDistance(),
			this.gameplayConfig.notificationsEnabled,
			this.gameplayConfig.notificationRetentionDays);
	}

	public synchronized void save() {
		if (!isLoaded()) {
			return;
		}

		boolean createBackups = ConfigManager.getInstance().getOpsSettings().createDataBackups;
		PersistentStateHelper.createDataDirectory(this.dataDirectory);
		writeJson(this.claimsFilePath, orderedClaims(), CLAIM_LIST_TYPE, "Safe Zone claims", createBackups,
			PersistentStateHelper.JsonOutput.COMPACT);
		writeJson(this.playerLimitsFilePath, new LinkedHashMap<>(this.playerLimits), PLAYER_LIMITS_TYPE,
			"Safe Zone player limits", createBackups, PersistentStateHelper.JsonOutput.COMPACT);
		writeJson(this.starterKitRecipientsFilePath, new ArrayList<>(this.starterKitRecipients), STARTER_KIT_RECIPIENTS_TYPE,
			"Safe Zone starter kit records", createBackups, PersistentStateHelper.JsonOutput.COMPACT);
	}

	public synchronized void unload() {
		this.server = null;
		this.dataDirectory = null;
		this.claimsFilePath = null;
		this.playerLimitsFilePath = null;
		this.starterKitRecipientsFilePath = null;
		this.claimsById.clear();
		this.claimLookupIndex.clear();
		this.playerLimits.clear();
		this.starterKitRecipients.clear();
		this.gameplayConfig = new GameplayConfig();
	}

	public synchronized boolean isLoaded() {
		return this.server != null;
	}

	public synchronized MinecraftServer getServer() {
		return this.server;
	}

	public synchronized List<ClaimData> getClaims() {
		return List.copyOf(orderedClaims());
	}

	public synchronized Optional<ClaimData> getClaim(String claimId) {
		return Optional.ofNullable(this.claimsById.get(claimId));
	}

	public synchronized Optional<ClaimData> getClaimAt(BlockPos pos) {
		return this.claimLookupIndex.getClaimAt(pos);
	}

	public synchronized List<ClaimData> getClaimsForOwner(UUID ownerId) {
		return this.claimLookupIndex.getClaimsForOwner(ownerId);
	}

	public synchronized List<ClaimData> getClaimsTrustedFor(UUID playerId) {
		return this.claimLookupIndex.getClaimsTrustedFor(playerId);
	}

	public synchronized int getMaxClaims(UUID playerId) {
		int configuredLimit = this.playerLimits.getOrDefault(playerId.toString(), this.gameplayConfig.defaultMaxClaims);
		return configuredLimit >= 1 ? configuredLimit : this.gameplayConfig.defaultMaxClaims;
	}

	public synchronized GameplayConfig getGameplayConfig() {
		return this.gameplayConfig.copy();
	}

	public synchronized long getClaimExpiryMillis() {
		return this.gameplayConfig.claimExpiryMillis();
	}

	public synchronized void setPlayerLimit(UUID playerId, int maxClaims) {
		if (maxClaims < 1) {
			throw new IllegalArgumentException("maxClaims must be at least 1");
		}

		requireLoaded();
		this.playerLimits.put(playerId.toString(), maxClaims);
		save();
	}

	public synchronized void clearPlayerLimit(UUID playerId) {
		requireLoaded();
		this.playerLimits.remove(playerId.toString());
		save();
	}

	public synchronized boolean hasReceivedStarterKit(UUID playerId) {
		requireLoaded();
		return this.starterKitRecipients.contains(playerId.toString());
	}

	public synchronized void markStarterKitReceived(UUID playerId) {
		requireLoaded();
		String playerUuid = playerId.toString();
		if (this.starterKitRecipients.contains(playerUuid)) {
			return;
		}

		this.starterKitRecipients.add(playerUuid);
		save();
	}

	public synchronized PermissionResult canBuild(ServerPlayer player, BlockPos pos) {
		Optional<ClaimData> claim = getClaimAt(pos);
		if (claim.isEmpty()) {
			return PermissionResult.OWNER;
		}

		ClaimData claimData = claim.get();
		UUID playerId = player.getUUID();
		if (claimData.owns(playerId)) {
			return PermissionResult.OWNER;
		}
		if (player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS))) {
			return PermissionResult.ADMIN_BYPASS;
		}
		if (claimData.isTrusted(playerId)) {
			return PermissionResult.TRUSTED;
		}
		return PermissionResult.DENIED;
	}

	public synchronized ClaimValidationResult validateNewClaim(Level level, UUID ownerId, BlockPos firstCorner, BlockPos secondCorner) {
		return validateClaimBounds(level, ownerId, firstCorner, secondCorner, null, true);
	}

	public synchronized ClaimValidationResult validateResizedClaim(String claimId, Level level, UUID ownerId, BlockPos fixedCorner, BlockPos newCorner) {
		requireLoaded();
		ClaimData claim = this.claimsById.get(claimId);
		if (claim == null) {
			throw new IllegalArgumentException("Unknown claim id: " + claimId);
		}
		return validateClaimBounds(level, ownerId, fixedCorner, newCorner, claimId, false);
	}

	public synchronized ClaimCreationResult createClaim(ServerPlayer owner, BlockPos firstCorner, BlockPos secondCorner) {
		requireLoaded();
		ClaimValidationResult validation = validateNewClaim(owner.level(), owner.getUUID(), firstCorner, secondCorner);
		if (!validation.isAllowed()) {
			return ClaimCreationResult.denied(validation.failure(), validation.conflictingClaim());
		}

		long now = System.currentTimeMillis();
		ClaimData claim = new ClaimData(this.claimIdGenerator.generateUniqueId(this.claimsById::containsKey),
			owner.getUUID().toString(), owner.getName().getString(), firstCorner, secondCorner, now);
		this.claimsById.put(claim.claimId, claim);
		this.claimLookupIndex.add(claim);
		save();
		return ClaimCreationResult.created(claim);
	}

	public synchronized ClaimCreationResult resizeClaim(ServerPlayer owner, String claimId, BlockPos fixedCorner, BlockPos newCorner) {
		requireLoaded();
		ClaimData claim = this.claimsById.get(claimId);
		if (claim == null) {
			throw new IllegalArgumentException("Unknown claim id: " + claimId);
		}

		ClaimValidationResult validation = validateResizedClaim(claimId, owner.level(), owner.getUUID(), fixedCorner, newCorner);
		if (!validation.isAllowed()) {
			return ClaimCreationResult.denied(validation.failure(), validation.conflictingClaim());
		}

		this.claimLookupIndex.remove(claim);
		claim.setCorners(fixedCorner, newCorner);
		claim.touch(System.currentTimeMillis());
		this.claimLookupIndex.add(claim);
		save();
		return ClaimCreationResult.created(claim);
	}

	public synchronized boolean setPlayerTrusted(String claimId, UUID playerId, boolean trusted) {
		return setPlayerTrusted(claimId, playerId, null, trusted);
	}

	public synchronized boolean setPlayerTrusted(String claimId, UUID playerId, String playerName, boolean trusted) {
		requireLoaded();
		ClaimData claim = this.claimsById.get(claimId);
		if (claim == null) {
			throw new IllegalArgumentException("Unknown claim id: " + claimId);
		}

		this.claimLookupIndex.remove(claim);
		boolean changed = trusted ? claim.addTrustedPlayer(playerId, playerName) : claim.removeTrustedPlayer(playerId);
		this.claimLookupIndex.add(claim);
		if (changed) {
			claim.touch(System.currentTimeMillis());
			save();
		}

		return changed;
	}

	public synchronized boolean toggleTrustedPlayer(String claimId, UUID playerId) {
		return toggleTrustedPlayer(claimId, playerId, null);
	}

	public synchronized boolean toggleTrustedPlayer(String claimId, UUID playerId, String playerName) {
		requireLoaded();
		ClaimData claim = this.claimsById.get(claimId);
		if (claim == null) {
			throw new IllegalArgumentException("Unknown claim id: " + claimId);
		}

		this.claimLookupIndex.remove(claim);
		boolean nowTrusted;
		if (claim.isTrusted(playerId)) {
			claim.removeTrustedPlayer(playerId);
			nowTrusted = false;
		} else {
			claim.addTrustedPlayer(playerId, playerName);
			nowTrusted = true;
		}

		claim.touch(System.currentTimeMillis());
		this.claimLookupIndex.add(claim);
		save();
		return nowTrusted;
	}

	public synchronized void transferClaim(String claimId, UUID newOwnerId, String newOwnerName) {
		requireLoaded();
		ClaimData claim = this.claimsById.get(claimId);
		if (claim == null) {
			throw new IllegalArgumentException("Unknown claim id: " + claimId);
		}

		this.claimLookupIndex.remove(claim);
		claim.ownerUuid = newOwnerId.toString();
		claim.ownerName = newOwnerName;
		claim.removeTrustedPlayer(newOwnerId);
		claim.touch(System.currentTimeMillis());
		this.claimLookupIndex.add(claim);
		save();
	}

	public synchronized boolean removeClaim(String claimId) {
		requireLoaded();
		ClaimData removed = this.claimsById.remove(claimId);
		if (removed != null) {
			this.claimLookupIndex.remove(removed);
			save();
			return true;
		}

		return false;
	}

	public synchronized boolean touchClaim(String claimId) {
		ClaimData claim = this.claimsById.get(claimId);
		if (claim == null) {
			return false;
		}

		claim.touch(System.currentTimeMillis());
		save();
		return true;
	}

	private void requireLoaded() {
		if (!isLoaded()) {
			throw new IllegalStateException("ClaimManager is not attached to a server");
		}
	}

	private <T> T readJson(Path filePath, Type type, T fallback, String dataLabel, boolean recoverFromBackup) {
		return PersistentStateHelper.readJson(filePath, type, fallback, dataLabel, recoverFromBackup);
	}

	private void writeJson(Path filePath, Object value, Type type, String dataLabel, boolean createBackup,
		PersistentStateHelper.JsonOutput jsonOutput) {
		PersistentStateHelper.writeJsonAtomically(filePath, value, type, dataLabel, createBackup, jsonOutput);
	}

	private List<ClaimData> orderedClaims() {
		List<ClaimData> claims = new ArrayList<>(this.claimsById.values());
		claims.sort(Comparator.comparingLong((ClaimData claim) -> claim.createdAt).thenComparing(claim -> claim.claimId));
		return claims;
	}

	private ClaimValidationResult validateClaimBounds(Level level, UUID ownerId, BlockPos firstCorner, BlockPos secondCorner,
		String ignoredClaimId, boolean enforceClaimLimit) {
		requireLoaded();
		int minClaimDistance = this.gameplayConfig.effectiveMinDistance();
		return this.claimValidator.validate(level.dimension() == Level.OVERWORLD, ownerId, firstCorner, secondCorner,
			this.claimLookupIndex.getNearbyClaims(firstCorner, secondCorner, minClaimDistance, ignoredClaimId),
			this.claimLookupIndex.getClaimCountForOwner(ownerId), getMaxClaims(ownerId), this.gameplayConfig.maxClaimWidth,
			this.gameplayConfig.maxClaimDepth, minClaimDistance, ignoredClaimId, enforceClaimLimit);
	}

	public enum ClaimValidationFailure {
		DIMENSION_NOT_ALLOWED,
		CLAIM_LIMIT_REACHED,
		CLAIM_TOO_LARGE,
		TOO_CLOSE_TO_EXISTING_CLAIM
	}

	public record ClaimValidationResult(ClaimValidationFailure failure, ClaimData conflictingClaim) {
		public static ClaimValidationResult allowed() {
			return new ClaimValidationResult(null, null);
		}

		public static ClaimValidationResult denied(ClaimValidationFailure failure, ClaimData conflictingClaim) {
			return new ClaimValidationResult(Objects.requireNonNull(failure, "failure"), conflictingClaim);
		}

		public boolean isAllowed() {
			return this.failure == null;
		}
	}

	public record ClaimCreationResult(ClaimData claim, ClaimValidationFailure failure, ClaimData conflictingClaim) {
		public static ClaimCreationResult created(ClaimData claim) {
			return new ClaimCreationResult(Objects.requireNonNull(claim, "claim"), null, null);
		}

		public static ClaimCreationResult denied(ClaimValidationFailure failure, ClaimData conflictingClaim) {
			return new ClaimCreationResult(null, Objects.requireNonNull(failure, "failure"), conflictingClaim);
		}

		public boolean created() {
			return this.claim != null;
		}
	}
}
