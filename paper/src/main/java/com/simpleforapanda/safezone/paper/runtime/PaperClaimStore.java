package com.simpleforapanda.safezone.paper.runtime;

import com.simpleforapanda.safezone.data.ClaimCoordinates;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.PermissionResult;
import com.simpleforapanda.safezone.data.SafeZoneConfig;
import com.simpleforapanda.safezone.manager.ClaimExpiryRefreshResult;
import com.simpleforapanda.safezone.manager.ClaimCreationResult;
import com.simpleforapanda.safezone.manager.ClaimValidationResult;
import com.simpleforapanda.safezone.manager.CommonClaimService;
import com.simpleforapanda.safezone.manager.PersistentStateHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class PaperClaimStore {
	private final CommonClaimService service;

	PaperClaimStore(Logger logger, PaperPathLayout paths) {
		Objects.requireNonNull(logger, "logger");
		this.service = new CommonClaimService(
			Objects.requireNonNull(paths, "paths"),
			PersistentStateHelper.JsonOutput.PRETTY,
			logger::info,
			"Safe Zone Paper");
	}

	public synchronized void load(SafeZoneConfig config) {
		Objects.requireNonNull(config, "config");
		this.service.load(config.gameplay, config.ops);
	}

	public synchronized int countClaims() {
		return this.service.countClaims();
	}

	public synchronized int countPlayerLimitOverrides() {
		return this.service.countPlayerLimitOverrides();
	}

	public synchronized int countStarterKitRecipients() {
		return this.service.countStarterKitRecipients();
	}

	public synchronized void save() {
		this.service.save();
	}

	public synchronized int countOwners() {
		return this.service.countOwners();
	}

	public synchronized List<ClaimData> getClaims() {
		return this.service.getClaims();
	}

	public synchronized List<String> getClaimIds() {
		return this.service.getClaimIds();
	}

	public synchronized Optional<ClaimData> getClaim(String claimId) {
		return this.service.getClaim(claimId);
	}

	public synchronized Optional<ClaimData> getClaimAt(Location location) {
		Objects.requireNonNull(location, "location");
		if (location.getWorld() == null || !isClaimWorld(location.getWorld())) {
			return Optional.empty();
		}
		return this.service.getClaimAt(new ClaimCoordinates(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
	}

	public synchronized List<ClaimData> getClaimsForOwner(UUID ownerId) {
		return this.service.getClaimsForOwner(ownerId);
	}

	public synchronized List<ClaimData> getClaimsTrustedFor(UUID playerId) {
		return this.service.getClaimsTrustedFor(playerId);
	}

	public synchronized int getMaxClaims(UUID playerId, int defaultMaxClaims) {
		Optional<Integer> override = this.service.getPlayerLimitOverride(playerId);
		int resolvedDefault = this.service.getGameplayConfig().defaultMaxClaims;
		int effectiveDefault = resolvedDefault >= 1 ? resolvedDefault : defaultMaxClaims;
		return override.filter(limit -> limit >= 1).orElse(effectiveDefault);
	}

	public synchronized Optional<Integer> getPlayerLimitOverride(UUID playerId) {
		return this.service.getPlayerLimitOverride(playerId);
	}

	public synchronized void setPlayerLimit(UUID playerId, int maxClaims) {
		this.service.setPlayerLimit(playerId, maxClaims);
	}

	public synchronized void clearPlayerLimit(UUID playerId) {
		this.service.clearPlayerLimit(playerId);
	}

	public synchronized boolean hasReceivedStarterKit(UUID playerId) {
		return this.service.hasReceivedStarterKit(playerId);
	}

	public synchronized ClaimExpiryRefreshResult refreshOwnerClaimsOnLogin(UUID ownerId) {
		return this.service.refreshOwnerClaimsOnLogin(ownerId);
	}

	public synchronized void markStarterKitReceived(UUID playerId) {
		this.service.markStarterKitReceived(playerId);
	}

	public synchronized boolean isClaimShowEnabled(UUID playerId) {
		return this.service.isClaimShowEnabled(playerId);
	}

	public synchronized boolean toggleClaimShow(UUID playerId) {
		return this.service.toggleClaimShow(playerId);
	}

	public synchronized PermissionResult getPermission(ClaimData claim, UUID playerId, boolean adminBypass) {
		return this.service.getPermission(claim, playerId, adminBypass);
	}

	public synchronized ClaimValidationResult validateResizedClaim(
		World world,
		UUID ownerId,
		String claimId,
		ClaimCoordinates fixedCorner,
		ClaimCoordinates newCorner
	) {
		return this.service.validateResizedClaim(isClaimWorld(world), ownerId, claimId, fixedCorner, newCorner);
	}

	public synchronized ClaimValidationResult validateNewClaim(
		World world,
		UUID ownerId,
		ClaimCoordinates firstCorner,
		ClaimCoordinates secondCorner
	) {
		return this.service.validateNewClaim(isClaimWorld(world), ownerId, firstCorner, secondCorner);
	}

	public synchronized ClaimCreationResult createClaim(World world, UUID ownerId, String ownerName, ClaimCoordinates firstCorner, ClaimCoordinates secondCorner) {
		return this.service.createClaim(isClaimWorld(world), ownerId, ownerName, firstCorner, secondCorner);
	}

	public synchronized ClaimCreationResult resizeClaim(World world, UUID ownerId, String claimId, ClaimCoordinates fixedCorner, ClaimCoordinates newCorner) {
		return this.service.resizeClaim(isClaimWorld(world), ownerId, claimId, fixedCorner, newCorner);
	}

	public synchronized boolean removeClaim(String claimId) {
		return this.service.removeClaim(claimId);
	}

	public synchronized boolean setPlayerTrusted(String claimId, UUID playerId, boolean trusted) {
		return this.service.setPlayerTrusted(claimId, playerId, null, trusted);
	}

	public synchronized boolean setPlayerTrusted(String claimId, UUID playerId, String playerName, boolean trusted) {
		return this.service.setPlayerTrusted(claimId, playerId, playerName, trusted);
	}

	public synchronized boolean toggleTrustedPlayer(String claimId, UUID playerId) {
		return this.service.toggleTrustedPlayer(claimId, playerId, null);
	}

	public synchronized boolean toggleTrustedPlayer(String claimId, UUID playerId, String playerName) {
		return this.service.toggleTrustedPlayer(claimId, playerId, playerName);
	}

	public synchronized void transferClaim(String claimId, UUID newOwnerId, String newOwnerName) {
		this.service.transferClaim(claimId, newOwnerId, newOwnerName);
	}

	public boolean isClaimWorld(World world) {
		return world != null
			&& world.getEnvironment() == World.Environment.NORMAL
			&& world.equals(Bukkit.getWorlds().getFirst());
	}

	public Optional<World> getClaimWorld() {
		return Bukkit.getWorlds().stream().filter(this::isClaimWorld).findFirst();
	}
}
