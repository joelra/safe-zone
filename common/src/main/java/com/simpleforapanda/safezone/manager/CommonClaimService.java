package com.simpleforapanda.safezone.manager;

import com.google.gson.reflect.TypeToken;
import com.simpleforapanda.safezone.data.ClaimCoordinates;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.data.OpsSettings;
import com.simpleforapanda.safezone.data.PermissionResult;
import com.simpleforapanda.safezone.port.PathLayoutPort;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class CommonClaimService {
	private static final Type CLAIM_LIST_TYPE = new TypeToken<List<ClaimData>>() { }.getType();
	private static final Type PLAYER_LIMITS_TYPE = new TypeToken<Map<String, Integer>>() { }.getType();
	private static final Type STARTER_KIT_RECIPIENTS_TYPE = new TypeToken<List<String>>() { }.getType();

	private final PathLayoutPort paths;
	private final ClaimValidator claimValidator;
	private final ClaimIdGenerator claimIdGenerator;
	private final ClaimLookupIndex claimLookupIndex = new ClaimLookupIndex();
	private final Map<String, ClaimData> claimsById = new LinkedHashMap<>();
	private final Map<String, Integer> playerLimits = new HashMap<>();
	private final Set<String> starterKitRecipients = new LinkedHashSet<>();
	private final PersistentStateHelper.JsonOutput jsonOutput;
	private final Consumer<String> infoLogger;
	private final String labelPrefix;

	private GameplayConfig gameplayConfig = new GameplayConfig();
	private OpsSettings opsSettings = new OpsSettings();
	private boolean loaded;

	public CommonClaimService(
		PathLayoutPort paths,
		PersistentStateHelper.JsonOutput jsonOutput,
		Consumer<String> infoLogger,
		String labelPrefix
	) {
		this(paths, new ClaimValidator(), new ClaimIdGenerator(), jsonOutput, infoLogger, labelPrefix);
	}

	CommonClaimService(
		PathLayoutPort paths,
		ClaimValidator claimValidator,
		ClaimIdGenerator claimIdGenerator,
		PersistentStateHelper.JsonOutput jsonOutput,
		Consumer<String> infoLogger,
		String labelPrefix
	) {
		this.paths = Objects.requireNonNull(paths, "paths");
		this.claimValidator = Objects.requireNonNull(claimValidator, "claimValidator");
		this.claimIdGenerator = Objects.requireNonNull(claimIdGenerator, "claimIdGenerator");
		this.jsonOutput = Objects.requireNonNull(jsonOutput, "jsonOutput");
		this.infoLogger = Objects.requireNonNull(infoLogger, "infoLogger");
		this.labelPrefix = Objects.requireNonNull(labelPrefix, "labelPrefix");
	}

	public synchronized void load(GameplayConfig gameplayConfig, OpsSettings opsSettings) {
		Objects.requireNonNull(gameplayConfig, "gameplayConfig");
		Objects.requireNonNull(opsSettings, "opsSettings");
		this.paths.ensureDirectories();
		this.gameplayConfig = gameplayConfig.copy();
		this.opsSettings = opsSettings.copy();

		List<ClaimData> loadedClaims = PersistentStateHelper.readJson(
			this.paths.claimsFile(),
			CLAIM_LIST_TYPE,
			List.<ClaimData>of(),
			this.labelPrefix + " claims",
			this.opsSettings.recoverFromBackupOnLoadFailure);
		Map<String, Integer> loadedPlayerLimits = new HashMap<>(PersistentStateHelper.readJson(
			this.paths.playerLimitsFile(),
			PLAYER_LIMITS_TYPE,
			Map.of(),
			this.labelPrefix + " player limits",
			this.opsSettings.recoverFromBackupOnLoadFailure));
		List<String> loadedStarterKitRecipients = new ArrayList<>(PersistentStateHelper.readJson(
			this.paths.starterKitRecipientsFile(),
			STARTER_KIT_RECIPIENTS_TYPE,
			List.of(),
			this.labelPrefix + " starter kit records",
			this.opsSettings.recoverFromBackupOnLoadFailure));

		LinkedHashMap<String, ClaimData> loadedClaimsById = new LinkedHashMap<>();
		for (ClaimData claim : loadedClaims) {
			claim.ensureDefaults();
			loadedClaimsById.put(claim.claimId, claim);
		}
		Map<String, Integer> normalizedPlayerLimits = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : loadedPlayerLimits.entrySet()) {
			if (entry.getValue() == null || entry.getValue() < 1) {
				continue;
			}
			normalizedPlayerLimits.put(ClaimData.normalizePlayerId(entry.getKey()), entry.getValue());
		}
		LinkedHashSet<String> normalizedStarterKitRecipients = new LinkedHashSet<>();
		for (String playerId : loadedStarterKitRecipients) {
			normalizedStarterKitRecipients.add(ClaimData.normalizePlayerId(playerId));
		}

		this.claimsById.clear();
		this.claimsById.putAll(loadedClaimsById);
		this.claimLookupIndex.rebuild(this.claimsById.values());
		this.playerLimits.clear();
		this.playerLimits.putAll(normalizedPlayerLimits);
		this.starterKitRecipients.clear();
		this.starterKitRecipients.addAll(normalizedStarterKitRecipients);
		this.loaded = true;

		this.infoLogger.accept(
			"Loaded %d claims, %d player limit overrides, %d starter kit records from %s"
				.formatted(this.claimsById.size(), this.playerLimits.size(), this.starterKitRecipients.size(), this.paths.dataDirectory()));
	}

	public synchronized void save() {
		if (!this.loaded) {
			return;
		}

		this.paths.ensureDirectories();
		PersistentStateHelper.writeJsonAtomically(
			this.paths.claimsFile(),
			orderedClaims(),
			CLAIM_LIST_TYPE,
			this.labelPrefix + " claims",
			this.opsSettings.createDataBackups,
			this.jsonOutput);
		PersistentStateHelper.writeJsonAtomically(
			this.paths.playerLimitsFile(),
			new LinkedHashMap<>(this.playerLimits),
			PLAYER_LIMITS_TYPE,
			this.labelPrefix + " player limits",
			this.opsSettings.createDataBackups,
			this.jsonOutput);
		PersistentStateHelper.writeJsonAtomically(
			this.paths.starterKitRecipientsFile(),
			new ArrayList<>(this.starterKitRecipients),
			STARTER_KIT_RECIPIENTS_TYPE,
			this.labelPrefix + " starter kit records",
			this.opsSettings.createDataBackups,
			this.jsonOutput);
	}

	public synchronized void unload() {
		this.claimsById.clear();
		this.claimLookupIndex.clear();
		this.playerLimits.clear();
		this.starterKitRecipients.clear();
		this.gameplayConfig = new GameplayConfig();
		this.opsSettings = new OpsSettings();
		this.loaded = false;
	}

	public synchronized boolean isLoaded() {
		return this.loaded;
	}

	public synchronized int countClaims() {
		return this.claimsById.size();
	}

	public synchronized int countOwners() {
		return (int) this.claimsById.values().stream().map(claim -> claim.ownerUuid).distinct().count();
	}

	public synchronized int countPlayerLimitOverrides() {
		return this.playerLimits.size();
	}

	public synchronized int countStarterKitRecipients() {
		return this.starterKitRecipients.size();
	}

	public synchronized List<ClaimData> getClaims() {
		return List.copyOf(orderedClaims());
	}

	public synchronized List<String> getClaimIds() {
		return orderedClaims().stream().map(claim -> claim.claimId).toList();
	}

	public synchronized Optional<ClaimData> getClaim(String claimId) {
		return Optional.ofNullable(this.claimsById.get(claimId));
	}

	public synchronized Optional<ClaimData> getClaimAt(ClaimCoordinates pos) {
		Objects.requireNonNull(pos, "pos");
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

	public synchronized Optional<Integer> getPlayerLimitOverride(UUID playerId) {
		return Optional.ofNullable(this.playerLimits.get(playerId.toString()));
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
		if (this.playerLimits.remove(playerId.toString()) != null) {
			save();
		}
	}

	public synchronized boolean hasReceivedStarterKit(UUID playerId) {
		requireLoaded();
		return this.starterKitRecipients.contains(playerId.toString());
	}

	public synchronized void markStarterKitReceived(UUID playerId) {
		requireLoaded();
		String playerUuid = Objects.requireNonNull(playerId, "playerId").toString();
		if (this.starterKitRecipients.contains(playerUuid)) {
			return;
		}

		this.starterKitRecipients.add(playerUuid);
		save();
	}

	public synchronized PermissionResult getPermission(ClaimData claim, UUID playerId, boolean adminBypass) {
		Objects.requireNonNull(claim, "claim");
		Objects.requireNonNull(playerId, "playerId");
		if (claim.owns(playerId)) {
			return PermissionResult.OWNER;
		}
		if (adminBypass) {
			return PermissionResult.ADMIN_BYPASS;
		}
		if (claim.isTrusted(playerId)) {
			return PermissionResult.TRUSTED;
		}
		return PermissionResult.DENIED;
	}

	public synchronized ClaimValidationResult validateNewClaim(
		boolean isClaimWorld,
		UUID ownerId,
		ClaimCoordinates firstCorner,
		ClaimCoordinates secondCorner
	) {
		return validateClaimBounds(isClaimWorld, ownerId, firstCorner, secondCorner, null, true);
	}

	public synchronized ClaimValidationResult validateResizedClaim(
		boolean isClaimWorld,
		UUID ownerId,
		String claimId,
		ClaimCoordinates fixedCorner,
		ClaimCoordinates newCorner
	) {
		requireLoaded();
		if (!this.claimsById.containsKey(claimId)) {
			throw new IllegalArgumentException("Unknown claim id: " + claimId);
		}
		return validateClaimBounds(isClaimWorld, ownerId, fixedCorner, newCorner, claimId, false);
	}

	public synchronized ClaimCreationResult createClaim(
		boolean isClaimWorld,
		UUID ownerId,
		String ownerName,
		ClaimCoordinates firstCorner,
		ClaimCoordinates secondCorner
	) {
		requireLoaded();
		ClaimValidationResult validation = validateNewClaim(isClaimWorld, ownerId, firstCorner, secondCorner);
		if (!validation.isAllowed()) {
			return ClaimCreationResult.denied(validation.failure(), validation.conflictingClaim());
		}

		long now = System.currentTimeMillis();
		ClaimData claim = new ClaimData(
			this.claimIdGenerator.generateUniqueId(this.claimsById::containsKey),
			ownerId.toString(),
			ownerName,
			firstCorner,
			secondCorner,
			now);
		this.claimsById.put(claim.claimId, claim);
		this.claimLookupIndex.add(claim);
		save();
		return ClaimCreationResult.created(claim);
	}

	public synchronized ClaimCreationResult resizeClaim(
		boolean isClaimWorld,
		UUID ownerId,
		String claimId,
		ClaimCoordinates fixedCorner,
		ClaimCoordinates newCorner
	) {
		requireLoaded();
		ClaimData claim = this.claimsById.get(claimId);
		if (claim == null) {
			throw new IllegalArgumentException("Unknown claim id: " + claimId);
		}

		ClaimValidationResult validation = validateResizedClaim(isClaimWorld, ownerId, claimId, fixedCorner, newCorner);
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

	public synchronized boolean removeClaim(String claimId) {
		requireLoaded();
		ClaimData removed = this.claimsById.remove(claimId);
		if (removed == null) {
			return false;
		}

		this.claimLookupIndex.remove(removed);
		save();
		return true;
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

	public synchronized boolean touchClaim(String claimId) {
		ClaimData claim = this.claimsById.get(claimId);
		if (claim == null) {
			return false;
		}

		claim.touch(System.currentTimeMillis());
		save();
		return true;
	}

	public synchronized ClaimExpiryRefreshResult refreshOwnerClaimsOnLogin(UUID ownerId) {
		return refreshOwnerClaimsOnLogin(ownerId, System.currentTimeMillis());
	}

	synchronized ClaimExpiryRefreshResult refreshOwnerClaimsOnLogin(UUID ownerId, long now) {
		requireLoaded();
		Objects.requireNonNull(ownerId, "ownerId");

		List<ClaimData> ownedClaims = new ArrayList<>(this.claimLookupIndex.getClaimsForOwner(ownerId));
		if (ownedClaims.isEmpty()) {
			return ClaimExpiryRefreshResult.empty();
		}

		long expiryMillis = this.gameplayConfig.claimExpiryMillis();
		List<String> expiredClaimIds = new ArrayList<>();
		int refreshedClaimCount = 0;
		boolean changed = false;
		for (ClaimData claim : ownedClaims) {
			long lastActiveAt = Math.max(claim.createdAt, claim.lastActiveAt);
			boolean expired = expiryMillis > 0L && now - lastActiveAt >= expiryMillis;
			if (expired) {
				this.claimsById.remove(claim.claimId);
				this.claimLookupIndex.remove(claim);
				expiredClaimIds.add(claim.claimId);
				changed = true;
				continue;
			}

			if (claim.lastActiveAt != now) {
				claim.touch(now);
				changed = true;
			}
			refreshedClaimCount++;
		}

		if (changed) {
			save();
		}
		return new ClaimExpiryRefreshResult(expiredClaimIds, refreshedClaimCount);
	}

	private void requireLoaded() {
		if (!this.loaded) {
			throw new IllegalStateException("CommonClaimService is not loaded");
		}
	}

	private List<ClaimData> orderedClaims() {
		List<ClaimData> claims = new ArrayList<>(this.claimsById.values());
		claims.sort(Comparator.comparingLong((ClaimData claim) -> claim.createdAt).thenComparing(claim -> claim.claimId));
		return claims;
	}

	private ClaimValidationResult validateClaimBounds(
		boolean isClaimWorld,
		UUID ownerId,
		ClaimCoordinates firstCorner,
		ClaimCoordinates secondCorner,
		String ignoredClaimId,
		boolean enforceClaimLimit
	) {
		requireLoaded();
		int minClaimDistance = this.gameplayConfig.effectiveMinDistance();
		Collection<ClaimData> nearbyClaims = this.claimLookupIndex.getNearbyClaims(firstCorner, secondCorner, minClaimDistance, ignoredClaimId);
		return this.claimValidator.validate(
			isClaimWorld,
			ownerId,
			firstCorner,
			secondCorner,
			nearbyClaims,
			this.claimLookupIndex.getClaimCountForOwner(ownerId),
			getMaxClaims(ownerId),
			this.gameplayConfig.maxClaimWidth,
			this.gameplayConfig.maxClaimDepth,
			minClaimDistance,
			ignoredClaimId,
			enforceClaimLimit);
	}
}
