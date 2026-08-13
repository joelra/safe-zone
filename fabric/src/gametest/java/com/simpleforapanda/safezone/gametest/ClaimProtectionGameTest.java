package com.simpleforapanda.safezone.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import com.simpleforapanda.safezone.data.ClaimData;

/**
 * Block break protection matrix: owner and trusted players may break inside a claim,
 * untrusted players may not.
 */
public final class ClaimProtectionGameTest {
	private static final BlockPos TARGET = new BlockPos(3, 2, 3);
	private static final Vec3 STAND_AT = new Vec3(3.5, 2.0, 2.0);

	@GameTest
	public void ownerCanBreakInOwnClaim(GameTestHelper helper) {
		ServerPlayer owner = SafeZoneGameTestSupport.playerAt(helper, STAND_AT);
		SafeZoneGameTestSupport.claimAround(helper, owner);
		helper.setBlock(TARGET, Blocks.STONE);

		owner.gameMode.destroyBlock(helper.absolutePos(TARGET));

		helper.assertBlockNotPresent(Blocks.STONE, TARGET);
		SafeZoneGameTestSupport.succeed(helper);
	}

	@GameTest
	public void untrustedCannotBreakInClaim(GameTestHelper helper) {
		ServerPlayer owner = SafeZoneGameTestSupport.playerAt(helper, STAND_AT);
		SafeZoneGameTestSupport.claimAround(helper, owner);
		helper.setBlock(TARGET, Blocks.STONE);

		ServerPlayer intruder = SafeZoneGameTestSupport.playerAt(helper, STAND_AT);
		intruder.gameMode.destroyBlock(helper.absolutePos(TARGET));

		helper.assertBlockPresent(Blocks.STONE, TARGET);
		SafeZoneGameTestSupport.succeed(helper);
	}

	@GameTest
	public void trustedCanBreakInClaim(GameTestHelper helper) {
		ServerPlayer owner = SafeZoneGameTestSupport.playerAt(helper, STAND_AT);
		ClaimData claim = SafeZoneGameTestSupport.claimAround(helper, owner);
		helper.setBlock(TARGET, Blocks.STONE);

		ServerPlayer friend = SafeZoneGameTestSupport.playerAt(helper, STAND_AT);
		SafeZoneGameTestSupport.trust(claim, friend);
		friend.gameMode.destroyBlock(helper.absolutePos(TARGET));

		helper.assertBlockNotPresent(Blocks.STONE, TARGET);
		SafeZoneGameTestSupport.succeed(helper);
	}

	@GameTest
	public void anyoneCanBreakInWilderness(GameTestHelper helper) {
		// Control: with no claim present, breaking works for everyone.
		helper.setBlock(TARGET, Blocks.STONE);

		ServerPlayer player = SafeZoneGameTestSupport.playerAt(helper, STAND_AT);
		player.gameMode.destroyBlock(helper.absolutePos(TARGET));

		helper.assertBlockNotPresent(Blocks.STONE, TARGET);
		SafeZoneGameTestSupport.succeed(helper);
	}
}
