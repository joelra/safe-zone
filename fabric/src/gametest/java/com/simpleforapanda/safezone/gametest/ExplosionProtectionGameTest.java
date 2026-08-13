package com.simpleforapanda.safezone.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Explosion protection inside claims: blocks are not destroyed and players with claim
 * access receive no explosion knockback. Wilderness controls prove the rig itself works.
 */
public final class ExplosionProtectionGameTest {
	private static final BlockPos TARGET = new BlockPos(3, 2, 3);
	private static final Vec3 PLAYER_POS = new Vec3(3.5, 2.0, 3.5);
	// 1.5 blocks away horizontally from the player, same height.
	private static final Vec3 BLAST_POS = new Vec3(5.0, 2.0, 3.5);

	@GameTest
	public void tntDoesNotBreakBlocksInClaim(GameTestHelper helper) {
		ServerPlayer owner = SafeZoneGameTestSupport.playerAt(helper, PLAYER_POS);
		SafeZoneGameTestSupport.claimAround(helper, owner);
		helper.setBlock(TARGET, Blocks.STONE);

		Vec3 blast = helper.absoluteVec(new Vec3(4.0, 2.5, 3.5));
		helper.getLevel().explode(null, blast.x, blast.y, blast.z, 4.0F, Level.ExplosionInteraction.TNT);

		helper.assertBlockPresent(Blocks.STONE, TARGET);
		SafeZoneGameTestSupport.succeed(helper);
	}

	@GameTest
	public void tntBreaksBlocksInWilderness(GameTestHelper helper) {
		// Control: the same explosion destroys the block when no claim protects it.
		helper.setBlock(TARGET, Blocks.STONE);

		Vec3 blast = helper.absoluteVec(new Vec3(4.0, 2.5, 3.5));
		helper.getLevel().explode(null, blast.x, blast.y, blast.z, 4.0F, Level.ExplosionInteraction.TNT);

		helper.assertBlockNotPresent(Blocks.STONE, TARGET);
		SafeZoneGameTestSupport.succeed(helper);
	}

	@GameTest
	public void tntKnockbackSuppressedInOwnClaim(GameTestHelper helper) {
		ServerPlayer owner = SafeZoneGameTestSupport.playerAt(helper, PLAYER_POS);
		SafeZoneGameTestSupport.claimAround(helper, owner);

		Vec3 blast = helper.absoluteVec(BLAST_POS);
		helper.getLevel().explode(null, blast.x, blast.y, blast.z, 1.2F, Level.ExplosionInteraction.NONE);

		if (owner.getDeltaMovement().lengthSqr() > 1.0E-4) {
			throw new IllegalStateException(
				"Explosion knockback should be suppressed for the owner inside their claim, but velocity was "
					+ owner.getDeltaMovement());
		}
		SafeZoneGameTestSupport.succeed(helper);
	}

	@GameTest
	public void tntKnockbackAppliesInWilderness(GameTestHelper helper) {
		// Control: without a claim, the same explosion knocks the player back.
		ServerPlayer player = SafeZoneGameTestSupport.playerAt(helper, PLAYER_POS);

		Vec3 blast = helper.absoluteVec(BLAST_POS);
		helper.getLevel().explode(null, blast.x, blast.y, blast.z, 1.2F, Level.ExplosionInteraction.NONE);

		if (player.getDeltaMovement().lengthSqr() < 1.0E-4) {
			throw new IllegalStateException("Expected explosion knockback in the wilderness, but velocity stayed "
				+ player.getDeltaMovement());
		}
		SafeZoneGameTestSupport.succeed(helper);
	}
}
