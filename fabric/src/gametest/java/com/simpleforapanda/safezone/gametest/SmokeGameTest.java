package com.simpleforapanda.safezone.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Minimal test proving the GameTest pipeline boots, discovers tests, and runs them.
 */
public final class SmokeGameTest {
	@GameTest
	public void frameworkBoots(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.GOLD_BLOCK);
		helper.assertBlockPresent(Blocks.GOLD_BLOCK, pos);
		helper.succeed();
	}
}
