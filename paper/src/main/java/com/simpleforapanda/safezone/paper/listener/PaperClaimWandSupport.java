package com.simpleforapanda.safezone.paper.listener;

import com.simpleforapanda.safezone.data.GameplayConfig;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class PaperClaimWandSupport {
	private PaperClaimWandSupport() {
	}

	public static boolean isClaimWand(ItemStack itemStack, GameplayConfig gameplayConfig) {
		return itemStack != null && itemStack.getType() == resolveClaimWandMaterial(gameplayConfig.claimWandItemId);
	}

	public static ItemStack createClaimWand(GameplayConfig gameplayConfig) {
		return new ItemStack(resolveClaimWandMaterial(gameplayConfig.claimWandItemId));
	}

	public static String claimWandName(GameplayConfig gameplayConfig) {
		return formatMaterialName(resolveClaimWandMaterial(gameplayConfig.claimWandItemId));
	}

	private static Material resolveClaimWandMaterial(String configuredItemId) {
		String normalized = configuredItemId == null || configuredItemId.isBlank()
			? GameplayConfig.DEFAULT_CLAIM_WAND_ITEM_ID
			: configuredItemId.trim().toLowerCase(Locale.ROOT);
		int namespaceSeparator = normalized.lastIndexOf(':');
		String materialName = (namespaceSeparator >= 0 ? normalized.substring(namespaceSeparator + 1) : normalized)
			.replace('-', '_')
			.toUpperCase(Locale.ROOT);
		Material material = Material.matchMaterial(materialName);
		return material == null ? Material.GOLDEN_HOE : material;
	}

	private static String formatMaterialName(Material material) {
		return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
	}
}
