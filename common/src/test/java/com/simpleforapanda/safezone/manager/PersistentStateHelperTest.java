package com.simpleforapanda.safezone.manager;

import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentStateHelperTest {
	private static final Type MAP_TYPE = new TypeToken<Map<String, Integer>>() { }.getType();

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
	void writeJsonAtomicallyCreatesBackupOnSecondSave() throws IOException {
		Path filePath = createTestFilePath("claims.json");

		PersistentStateHelper.writeJsonAtomically(filePath, Map.of("value", 1), MAP_TYPE, "test data", true,
			PersistentStateHelper.JsonOutput.COMPACT);
		assertFalse(Files.exists(PersistentStateHelper.backupPathFor(filePath)));

		PersistentStateHelper.writeJsonAtomically(filePath, Map.of("value", 2), MAP_TYPE, "test data", true,
			PersistentStateHelper.JsonOutput.COMPACT);

		assertTrue(Files.exists(PersistentStateHelper.backupPathFor(filePath)));
		assertEquals(Map.of("value", 2),
			PersistentStateHelper.readJson(filePath, MAP_TYPE, Map.of(), "test data", true));
		assertEquals(Map.of("value", 1),
			PersistentStateHelper.readJson(PersistentStateHelper.backupPathFor(filePath), MAP_TYPE, Map.of(), "test backup", false));
	}

	@Test
	void readJsonRecoversFromBackupAndRestoresPrimaryFile() throws IOException {
		Path filePath = createTestFilePath("notifications.json");

		PersistentStateHelper.writeJsonAtomically(filePath, Map.of("value", 1), MAP_TYPE, "test data", true,
			PersistentStateHelper.JsonOutput.COMPACT);
		PersistentStateHelper.writeJsonAtomically(filePath, Map.of("value", 2), MAP_TYPE, "test data", true,
			PersistentStateHelper.JsonOutput.COMPACT);
		Files.writeString(filePath, "{not valid json", StandardCharsets.UTF_8);

		Map<String, Integer> recovered = PersistentStateHelper.readJson(filePath, MAP_TYPE, Map.of(), "test data", true);

		assertEquals(Map.of("value", 1), recovered);
		assertEquals(Map.of("value", 1),
			PersistentStateHelper.readJson(filePath, MAP_TYPE, Map.of(), "test data", false));
	}

	@Test
	void writeJsonAtomicallyCompactsRuntimeJson() throws IOException {
		Path filePath = createTestFilePath("player_limits.json");

		PersistentStateHelper.writeJsonAtomically(filePath, Map.of("value", 2), MAP_TYPE, "test data", false,
			PersistentStateHelper.JsonOutput.COMPACT);

		assertEquals("{\"value\":2}", Files.readString(filePath, StandardCharsets.UTF_8));
	}

	@Test
	void writeJsonAtomicallyPrettyPrintsConfigJson() throws IOException {
		Path filePath = createTestFilePath("config.json");

		PersistentStateHelper.writeJsonAtomically(filePath, Map.of("value", 2), MAP_TYPE, "test data", false,
			PersistentStateHelper.JsonOutput.PRETTY);

		assertEquals("{\n  \"value\": 2\n}", Files.readString(filePath, StandardCharsets.UTF_8));
	}

	// Crash-corruption resilience tests.
	//
	// On a hard crash (OOM kill, power loss), the OS page cache may not have been
	// flushed. A file opened with TRUNCATE_EXISTING gets zero-filled blocks on disk
	// even though the JVM wrote valid content to the page cache. The result is a data
	// file (or its .tmp predecessor) full of null bytes.

	@Test
	void readJsonReturnsFallbackForNullByteDataFile() throws IOException {
		Path filePath = createTestFilePath("player_limits.json");
		Files.write(filePath, new byte[]{0, 0, 0, 0});

		Map<String, Integer> result = PersistentStateHelper.readJson(filePath, MAP_TYPE, Map.of(), "test data", false);

		assertEquals(Map.of(), result);
	}

	@Test
	void readJsonReturnsFallbackForWhitespaceOnlyDataFile() throws IOException {
		Path filePath = createTestFilePath("player_limits.json");
		Files.writeString(filePath, "   \n\t  ", StandardCharsets.UTF_8);

		Map<String, Integer> result = PersistentStateHelper.readJson(filePath, MAP_TYPE, Map.of(), "test data", false);

		assertEquals(Map.of(), result);
	}

	@Test
	void readJsonStillFailsForActuallyCorruptJson() throws IOException {
		// Distinguishes "empty file" (safe to ignore) from genuinely corrupt data
		// (should not silently discard, so the operator is alerted).
		Path filePath = createTestFilePath("player_limits.json");
		Files.writeString(filePath, "{truncated", StandardCharsets.UTF_8);

		assertThrows(IllegalStateException.class,
			() -> PersistentStateHelper.readJson(filePath, MAP_TYPE, Map.of(), "test data", false));
	}

	@Test
	void cleanupStaleTempFileDeletesNullByteTempFile() throws IOException {
		Path filePath = createTestFilePath("player_limits.json");
		Path tempPath = filePath.resolveSibling("player_limits.json.tmp");
		Files.write(tempPath, new byte[]{0, 0, 0, 0});

		PersistentStateHelper.cleanupStaleTempFile(filePath);

		assertFalse(Files.exists(tempPath));
	}

	@Test
	void cleanupStaleTempFileIsNoOpWhenTempAbsent() throws IOException {
		Path filePath = createTestFilePath("player_limits.json");

		// Should not throw even when the .tmp does not exist
		PersistentStateHelper.cleanupStaleTempFile(filePath);

		assertFalse(Files.exists(filePath.resolveSibling("player_limits.json.tmp")));
	}

	@Test
	void crashRecoveryFlowStaleTempPlusIntactDataFile() throws IOException {
		// Simulates: crash happened mid-write (temp has null bytes), but the previous
		// save of the real file completed successfully. On startup, cleanup removes the
		// stale temp and the load reads the intact destination.
		Path filePath = createTestFilePath("player_limits.json");
		Path tempPath = filePath.resolveSibling("player_limits.json.tmp");

		PersistentStateHelper.writeJsonAtomically(filePath, Map.of("uuid-a", 5), MAP_TYPE, "test data", false,
			PersistentStateHelper.JsonOutput.COMPACT);
		Files.write(tempPath, new byte[128]); // null bytes left by a crashed write

		PersistentStateHelper.cleanupStaleTempFile(filePath);
		Map<String, Integer> loaded = PersistentStateHelper.readJson(filePath, MAP_TYPE, Map.of(), "test data", false);

		assertFalse(Files.exists(tempPath));
		assertEquals(Map.of("uuid-a", 5), loaded);
	}

	private Path createTestFilePath(String fileName) throws IOException {
		this.testDirectory = Path.of("build", "test-work", getClass().getSimpleName(), UUID.randomUUID().toString());
		Files.createDirectories(this.testDirectory);
		return this.testDirectory.resolve(fileName);
	}
}
