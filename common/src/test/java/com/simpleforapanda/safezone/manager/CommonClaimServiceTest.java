package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.data.ClaimCoordinates;
import com.simpleforapanda.safezone.data.GameplayConfig;
import com.simpleforapanda.safezone.data.OpsSettings;
import com.simpleforapanda.safezone.port.PathLayoutPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonClaimServiceTest {
	private Path testDirectory;

	@AfterEach
	void tearDown() throws IOException {
		if (this.testDirectory == null || Files.notExists(this.testDirectory)) {
			return;
		}

		try (Stream<Path> paths = Files.walk(this.testDirectory)) {
			paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
				.forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException exception) {
						throw new RuntimeException(exception);
					}
				});
		}
	}

	@Test
	void refreshOwnerClaimsOnLoginTouchesClaimsWhenExpiryDisabled() throws IOException {
		CommonClaimService service = createService();
		GameplayConfig gameplayConfig = new GameplayConfig();
		gameplayConfig.claimExpiryDays = 0;
		service.load(gameplayConfig, new OpsSettings());

		UUID ownerId = UUID.randomUUID();
		ClaimCreationResult created = service.createClaim(true, ownerId, "Owner",
			new ClaimCoordinates(0, 64, 0),
			new ClaimCoordinates(4, 64, 4));
		assertTrue(created.created());
		service.getClaim(created.claim().claimId).orElseThrow().touch(10L);

		ClaimExpiryRefreshResult result = service.refreshOwnerClaimsOnLogin(ownerId, 50L);

		assertEquals(List.of(), result.expiredClaimIds());
		assertEquals(1, result.refreshedClaimCount());
		assertEquals(50L, service.getClaim(created.claim().claimId).orElseThrow().lastActiveAt);
	}

	@Test
	void refreshOwnerClaimsOnLoginRemovesExpiredClaimsBeforeTouching() throws IOException {
		CommonClaimService service = createService();
		GameplayConfig gameplayConfig = new GameplayConfig();
		gameplayConfig.claimExpiryDays = 5;
		service.load(gameplayConfig, new OpsSettings());

		UUID ownerId = UUID.randomUUID();
		ClaimCreationResult expired = service.createClaim(true, ownerId, "Owner",
			new ClaimCoordinates(0, 64, 0),
			new ClaimCoordinates(4, 64, 4));
		ClaimCreationResult active = service.createClaim(true, ownerId, "Owner",
			new ClaimCoordinates(10, 64, 10),
			new ClaimCoordinates(14, 64, 14));
		assertTrue(expired.created());
		assertTrue(active.created());

		long expiryMillis = gameplayConfig.claimExpiryMillis();
		long now = expired.claim().createdAt + expiryMillis + 10_000L;
		service.getClaim(expired.claim().claimId).orElseThrow().touch(now - expiryMillis);
		service.getClaim(active.claim().claimId).orElseThrow().touch(now - expiryMillis + 1L);

		ClaimExpiryRefreshResult result = service.refreshOwnerClaimsOnLogin(ownerId, now);

		assertEquals(List.of(expired.claim().claimId), result.expiredClaimIds());
		assertEquals(1, result.refreshedClaimCount());
		assertTrue(service.getClaim(expired.claim().claimId).isEmpty());
		assertFalse(service.getClaim(active.claim().claimId).isEmpty());
		assertEquals(now, service.getClaim(active.claim().claimId).orElseThrow().lastActiveAt);
	}

	@Test
	void loadNormalizesLegacyPlayerIdentifiers() throws IOException {
		CommonClaimService service = createService();
		UUID ownerId = UUID.randomUUID();
		UUID trustedId = UUID.randomUUID();
		UUID starterKitId = UUID.randomUUID();
		Path dataDirectory = this.testDirectory.resolve("data");
		Files.createDirectories(dataDirectory);
		Files.writeString(dataDirectory.resolve("claims.json"), """
			[
			  {
			    "claimId": "claim-1",
			    "ownerUuid": "%s",
			    "ownerName": "Owner",
			    "x1": 0,
			    "y1": 64,
			    "z1": 0,
			    "x2": 4,
			    "y2": 64,
			    "z2": 4,
			    "trusted": ["%s"],
			    "trustedNames": {"%s": "Builder"},
			    "createdAt": 100,
			    "lastActiveAt": 100
			  }
			]
			""".formatted(
			ownerId.toString().toUpperCase(Locale.ROOT),
			trustedId.toString().toUpperCase(Locale.ROOT),
			trustedId.toString().toUpperCase(Locale.ROOT)));
		Files.writeString(dataDirectory.resolve("player_limits.json"), """
			{"%s": 7}
			""".formatted(ownerId.toString().toUpperCase(Locale.ROOT)));
		Files.writeString(dataDirectory.resolve("starter_kit_recipients.json"), """
			["%s"]
			""".formatted(starterKitId.toString().toUpperCase(Locale.ROOT)));

		service.load(new GameplayConfig(), new OpsSettings());

		assertEquals(1, service.getClaimsForOwner(ownerId).size());
		assertEquals(1, service.getClaimsTrustedFor(trustedId).size());
		assertEquals(7, service.getPlayerLimitOverride(ownerId).orElseThrow());
		assertTrue(service.hasReceivedStarterKit(starterKitId));
	}

	private CommonClaimService createService() throws IOException {
		this.testDirectory = Path.of("build", "test-work", getClass().getSimpleName(), UUID.randomUUID().toString());
		Files.createDirectories(this.testDirectory);
		return new CommonClaimService(
			new TestPathLayout(this.testDirectory),
			PersistentStateHelper.JsonOutput.COMPACT,
			message -> { },
			"Test Claims");
	}

	private record TestPathLayout(Path root) implements PathLayoutPort {
		@Override
		public void ensureDirectories() {
			try {
				Files.createDirectories(pluginDirectory());
				Files.createDirectories(dataDirectory());
				Files.createDirectories(logDirectory());
			} catch (IOException exception) {
				throw new RuntimeException(exception);
			}
		}

		@Override
		public Path pluginDirectory() {
			return this.root;
		}

		@Override
		public Path dataDirectory() {
			return this.root.resolve("data");
		}

		@Override
		public Path logDirectory() {
			return this.root.resolve("logs");
		}

		@Override
		public Path configFile() {
			return this.root.resolve("config.json");
		}

		@Override
		public Path legacyGameplayConfigFile() {
			return this.root.resolve("gameplay.json");
		}

		@Override
		public Path legacyOpsSettingsFile() {
			return this.root.resolve("ops.json");
		}

		@Override
		public Path legacyClaimSettingsFile() {
			return this.root.resolve("claim-settings.json");
		}

		@Override
		public Path notificationsFile() {
			return dataDirectory().resolve("notifications.json");
		}

		@Override
		public Path claimsFile() {
			return dataDirectory().resolve("claims.json");
		}

		@Override
		public Path playerLimitsFile() {
			return dataDirectory().resolve("player_limits.json");
		}

		@Override
		public Path starterKitRecipientsFile() {
			return dataDirectory().resolve("starter_kit_recipients.json");
		}

		@Override
		public Path claimShowPreferencesFile() {
			return dataDirectory().resolve("claim_show_preferences.json");
		}

		@Override
		public Path auditLogFile() {
			return logDirectory().resolve("audit.log");
		}
	}
}
