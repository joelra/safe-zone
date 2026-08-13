package com.simpleforapanda.safezone.gametest;

import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.manager.ClaimCreationResult;
import com.simpleforapanda.safezone.manager.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shared helpers for Safe Zone in-game tests.
 *
 * <p>Tests run inside the default (empty) structure; all relative coordinates must stay
 * within its bounds. Claims are created through the real {@link ClaimManager} so tests
 * exercise the same code paths as players.
 */
final class SafeZoneGameTestSupport {
	/** Claim corners used by {@link #claimAround}: a 6x6 column inside the structure. */
	private static final BlockPos CLAIM_CORNER_A = new BlockPos(1, 1, 1);
	private static final BlockPos CLAIM_CORNER_B = new BlockPos(6, 1, 6);

	/**
	 * Mock players spawned per test, removed again in {@link #succeed}. Vanilla's test
	 * cleanup (and {@code /test clearall}) intentionally skips player entities, so
	 * without this they would accumulate on interactive test servers. Failed tests
	 * still leave theirs behind — `/test clearmockplayers` sweeps those up.
	 */
	private static final Map<GameTestHelper, List<ServerPlayer>> MOCK_PLAYERS = new WeakHashMap<>();

	private SafeZoneGameTestSupport() {
	}

	/** Spawns a mock server player and places it at the given structure-relative position. */
	static ServerPlayer playerAt(GameTestHelper helper, Vec3 relativePos) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 pos = helper.absoluteVec(relativePos);
		player.setPos(pos.x, pos.y, pos.z);
		player.setDeltaMovement(Vec3.ZERO);
		MOCK_PLAYERS.computeIfAbsent(helper, ignored -> new ArrayList<>()).add(player);
		return player;
	}

	/** Removes this test's mock players, then marks the test as passed. */
	static void succeed(GameTestHelper helper) {
		List<ServerPlayer> players = MOCK_PLAYERS.remove(helper);
		if (players != null) {
			for (ServerPlayer player : players) {
				helper.getLevel().getServer().getPlayerList().remove(player);
			}
		}
		helper.succeed();
	}

	/** Creates a claim owned by {@code owner} covering the interior of the test structure. */
	static ClaimData claimAround(GameTestHelper helper, ServerPlayer owner) {
		ClaimCreationResult result = ClaimManager.getInstance()
			.createClaim(owner, helper.absolutePos(CLAIM_CORNER_A), helper.absolutePos(CLAIM_CORNER_B));
		if (!result.created()) {
			throw new IllegalStateException("Test claim creation failed: " + result.failure()
				+ " (conflicting claim: " + (result.conflictingClaim() == null ? "none" : result.conflictingClaim().claimId) + ")");
		}
		return result.claim();
	}

	/** Marks {@code player} as trusted in {@code claim}. */
	static void trust(ClaimData claim, ServerPlayer player) {
		if (!ClaimManager.getInstance().setPlayerTrusted(claim.claimId, player.getUUID(), player.getName().getString(), true)) {
			throw new IllegalStateException("Failed to trust player in test claim " + claim.claimId);
		}
	}

	/** Runs {@code action} with the windChargeKnockbackInClaims config temporarily set. */
	static void withWindChargeKnockbackInClaims(boolean value, Runnable action) {
		ClaimManager manager = ClaimManager.getInstance();
		var original = manager.getGameplayConfig();
		var modified = original.copy();
		modified.windChargeKnockbackInClaims = value;
		manager.updateGameplayConfig(modified);
		try {
			action.run();
		} finally {
			manager.updateGameplayConfig(original);
		}
	}
}
