package com.simpleforapanda.safezone.paper.command;

import com.simpleforapanda.safezone.data.ClaimData;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class PaperCommandSupport {
	static final int CLAIMS_PER_PAGE = 8;

	private static final DateTimeFormatter TIMESTAMP_FORMATTER =
		DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private PaperCommandSupport() {
	}

	static String describeAccess(ClaimData claim, Player player, boolean adminAccess) {
		if (claim.owns(player.getUniqueId())) {
			return "owner";
		}
		if (claim.isTrusted(player.getUniqueId())) {
			return "trusted";
		}
		return adminAccess ? "admin-bypass" : "visitor";
	}

	static String formatBounds(ClaimData claim) {
		return "x=%d..%d, z=%d..%d".formatted(claim.getMinX(), claim.getMaxX(), claim.getMinZ(), claim.getMaxZ());
	}

	static String formatCenter(ClaimData claim) {
		var center = claim.getCenter();
		return center.x() + ", " + center.z();
	}

	static String formatTrustedSummary(ClaimData claim) {
		claim.ensureDefaults();
		if (claim.trusted.isEmpty()) {
			return "none";
		}

		List<String> names = new ArrayList<>();
		for (String trustedPlayer : claim.trusted) {
			String trustedName = claim.getTrustedName(trustedPlayer);
			names.add(trustedName == null || trustedName.isBlank() ? trustedPlayer : trustedName);
			if (names.size() == 3) {
				break;
			}
		}

		String summary = String.join(", ", names);
		if (claim.trusted.size() > names.size()) {
			summary += " +" + (claim.trusted.size() - names.size()) + " more";
		}
		return summary;
	}

	static String formatTimestamp(long timestamp) {
		return TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(timestamp));
	}
}
