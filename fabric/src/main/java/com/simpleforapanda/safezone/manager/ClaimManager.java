package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.SafeZone;
import com.simpleforapanda.safezone.data.ClaimCoordinates;
import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.data.PermissionResult;
import com.simpleforapanda.safezone.manager.ClaimExpiryRefreshResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ClaimManager {
	private static final ClaimManager INSTANCE = new ClaimManager();

	private MinecraftServer server;
	private CommonClaimService service;

	private ClaimManager() {
	}

	public static ClaimManager getInstance() {
		return INSTANCE;
	}

	public synchronized void load(MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		this.server = server;
		this.service = new CommonClaimService(
			FabricPathLayout.fromServer(server),
			PersistentStateHelper.JsonOutput.COMPACT,
			message -> SafeZone.LOGGER.info(message),
			"Safe Zone");
		this.service.load(ConfigManager.getInstance().getGameplayConfig(), ConfigManager.getInstance().getOpsSettings());

		GameplayConfig gameplayConfig = this.service.getGameplayConfig();
		SafeZone.LOGGER.info(
			"Loaded Safe Zone claim service: wand={}, default max claims={}, max size={}x{}, gap enforced={}, min gap={}, notifications enabled={}, notification retention={}d",
			gameplayConfig.claimWandItemId,
			gameplayConfig.defaultMaxClaims,
			gameplayConfig.maxClaimWidth,
			gameplayConfig.maxClaimDepth,
			gameplayConfig.claimGapEnforced,
			gameplayConfig.effectiveMinDistance(),
			gameplayConfig.notificationsEnabled,
			gameplayConfig.notificationRetentionDays);
	}

	public synchronized void save() {
		if (this.service != null) {
			this.service.save();
		}
	}

	public synchronized void unload() {
		if (this.service != null) {
			this.service.unload();
			this.service = null;
		}
		this.server = null;
	}

	public synchronized boolean isLoaded() {
		return this.server != null && this.service != null && this.service.isLoaded();
	}

	public synchronized MinecraftServer getServer() {
		return this.server;
	}

	public synchronized List<ClaimData> getClaims() {
		return requireService().getClaims();
	}

	public synchronized Optional<ClaimData> getClaim(String claimId) {
		return requireService().getClaim(claimId);
	}

	public synchronized Optional<ClaimData> getClaimAt(BlockPos pos) {
		return requireService().getClaimAt(toClaimCoordinates(pos));
	}

	public synchronized List<ClaimData> getClaimsForOwner(UUID ownerId) {
		return requireService().getClaimsForOwner(ownerId);
	}

	public synchronized List<ClaimData> getClaimsTrustedFor(UUID playerId) {
		return requireService().getClaimsTrustedFor(playerId);
	}

	public synchronized int getMaxClaims(UUID playerId) {
		return requireService().getMaxClaims(playerId);
	}

	public synchronized GameplayConfig getGameplayConfig() {
		return requireService().getGameplayConfig();
	}

	public synchronized long getClaimExpiryMillis() {
		return requireService().getClaimExpiryMillis();
	}

	public synchronized void setPlayerLimit(UUID playerId, int maxClaims) {
		requireService().setPlayerLimit(playerId, maxClaims);
	}

	public synchronized void clearPlayerLimit(UUID playerId) {
		requireService().clearPlayerLimit(playerId);
	}

	public synchronized boolean hasReceivedStarterKit(UUID playerId) {
		return requireService().hasReceivedStarterKit(playerId);
	}

	public synchronized void markStarterKitReceived(UUID playerId) {
		requireService().markStarterKitReceived(playerId);
	}

	public synchronized PermissionResult canBuild(ServerPlayer player, BlockPos pos) {
		Optional<ClaimData> claim = getClaimAt(pos);
		if (claim.isEmpty()) {
			return PermissionResult.OWNER;
		}

		return requireService().getPermission(
			claim.get(),
			player.getUUID(),
			player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)));
	}

	public synchronized ClaimValidationResult validateNewClaim(Level level, UUID ownerId, BlockPos firstCorner, BlockPos secondCorner) {
		return requireService().validateNewClaim(
			level.dimension() == Level.OVERWORLD,
			ownerId,
			toClaimCoordinates(firstCorner),
			toClaimCoordinates(secondCorner));
	}

	public synchronized ClaimValidationResult validateResizedClaim(String claimId, Level level, UUID ownerId, BlockPos fixedCorner, BlockPos newCorner) {
		return requireService().validateResizedClaim(
			level.dimension() == Level.OVERWORLD,
			ownerId,
			claimId,
			toClaimCoordinates(fixedCorner),
			toClaimCoordinates(newCorner));
	}

	public synchronized ClaimCreationResult createClaim(ServerPlayer owner, BlockPos firstCorner, BlockPos secondCorner) {
		return requireService().createClaim(
			owner.level().dimension() == Level.OVERWORLD,
			owner.getUUID(),
			owner.getName().getString(),
			toClaimCoordinates(firstCorner),
			toClaimCoordinates(secondCorner));
	}

	public synchronized ClaimCreationResult resizeClaim(ServerPlayer owner, String claimId, BlockPos fixedCorner, BlockPos newCorner) {
		return requireService().resizeClaim(
			owner.level().dimension() == Level.OVERWORLD,
			owner.getUUID(),
			claimId,
			toClaimCoordinates(fixedCorner),
			toClaimCoordinates(newCorner));
	}

	public synchronized boolean setPlayerTrusted(String claimId, UUID playerId, boolean trusted) {
		return requireService().setPlayerTrusted(claimId, playerId, null, trusted);
	}

	public synchronized boolean setPlayerTrusted(String claimId, UUID playerId, String playerName, boolean trusted) {
		return requireService().setPlayerTrusted(claimId, playerId, playerName, trusted);
	}

	public synchronized boolean toggleTrustedPlayer(String claimId, UUID playerId) {
		return requireService().toggleTrustedPlayer(claimId, playerId, null);
	}

	public synchronized boolean toggleTrustedPlayer(String claimId, UUID playerId, String playerName) {
		return requireService().toggleTrustedPlayer(claimId, playerId, playerName);
	}

	public synchronized void transferClaim(String claimId, UUID newOwnerId, String newOwnerName) {
		requireService().transferClaim(claimId, newOwnerId, newOwnerName);
	}

	public synchronized boolean removeClaim(String claimId) {
		return requireService().removeClaim(claimId);
	}

	public synchronized boolean touchClaim(String claimId) {
		return requireService().touchClaim(claimId);
	}

	public synchronized ClaimExpiryRefreshResult refreshOwnerClaimsOnLogin(UUID ownerId) {
		return requireService().refreshOwnerClaimsOnLogin(ownerId);
	}

	private CommonClaimService requireService() {
		if (this.service == null) {
			throw new IllegalStateException("ClaimManager is not attached to a server");
		}
		return this.service;
	}

	private static ClaimCoordinates toClaimCoordinates(BlockPos pos) {
		return new ClaimCoordinates(pos.getX(), pos.getY(), pos.getZ());
	}
}
