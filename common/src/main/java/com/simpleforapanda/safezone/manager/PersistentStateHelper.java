package com.simpleforapanda.safezone.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class PersistentStateHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger(PersistentStateHelper.class);

	public enum JsonOutput {
		PRETTY,
		COMPACT
	}

	private static final Gson READ_GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Gson COMPACT_GSON = new GsonBuilder().disableHtmlEscaping().create();

	private PersistentStateHelper() {
	}

	public static void createDataDirectory(Path dataDirectory) {
		try {
			Files.createDirectories(dataDirectory);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to create Safe Zone data directory: " + dataDirectory, exception);
		}
	}

	public static <T> T readJson(Path filePath, Type type, T fallback, String dataLabel, boolean recoverFromBackup) {
		if (Files.notExists(filePath)) {
			return fallback;
		}

		try {
			return readJsonFile(filePath, type, fallback);
		} catch (IOException | JsonParseException primaryException) {
			if (recoverFromBackup) {
				Path backupPath = backupPathFor(filePath);
				if (Files.exists(backupPath)) {
					LOGGER.warn("Failed to read {} from {}. Trying backup {}.",
						dataLabel, filePath, backupPath, primaryException);
					try {
						T recoveredValue = readJsonFile(backupPath, type, fallback);
						restoreBackup(filePath, backupPath, dataLabel);
						LOGGER.warn("Recovered {} from backup {}.", dataLabel, backupPath);
						return recoveredValue;
					} catch (IOException | JsonParseException backupException) {
						primaryException.addSuppressed(backupException);
					}
				}
			}

			throw new IllegalStateException("Failed to read " + dataLabel + " from " + filePath, primaryException);
		}
	}

	public static void cleanupStaleTempFile(Path filePath) {
		Path tempPath = tempPathFor(filePath);
		try {
			if (Files.deleteIfExists(tempPath)) {
				LOGGER.warn("Deleted stale temp file {} — likely left by a previous crash.", tempPath);
			}
		} catch (IOException exception) {
			LOGGER.warn("Failed to delete stale temp file {}.", tempPath, exception);
		}
	}

	public static void writeJsonAtomically(Path filePath, Object value, Type type, String dataLabel, boolean createBackup,
		JsonOutput jsonOutput) {
		Path tempPath = tempPathFor(filePath);
		try {
			try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE,
				StandardOpenOption.DSYNC)) {
				gsonFor(jsonOutput).toJson(value, type, writer);
			}

			if (createBackup && Files.exists(filePath)) {
				Files.copy(filePath, backupPathFor(filePath), StandardCopyOption.REPLACE_EXISTING);
			}

			moveReplacing(tempPath, filePath);
		} catch (IOException exception) {
			try {
				Files.deleteIfExists(tempPath);
			} catch (IOException cleanupException) {
				exception.addSuppressed(cleanupException);
			}
			throw new IllegalStateException("Failed to write " + dataLabel + " to " + filePath, exception);
		}
	}

	public static Path backupPathFor(Path filePath) {
		return filePath.resolveSibling(filePath.getFileName() + ".bak");
	}

	private static Path tempPathFor(Path filePath) {
		return filePath.resolveSibling(filePath.getFileName() + ".tmp");
	}

	private static Gson gsonFor(JsonOutput jsonOutput) {
		return jsonOutput == JsonOutput.PRETTY ? PRETTY_GSON : COMPACT_GSON;
	}

	private static <T> T readJsonFile(Path filePath, Type type, T fallback) throws IOException {
		String content = Files.readString(filePath, StandardCharsets.UTF_8).replace("\0", "").strip();
		if (content.isEmpty()) {
			return fallback;
		}
		T value = READ_GSON.fromJson(content, type);
		return value == null ? fallback : value;
	}

	private static void restoreBackup(Path targetPath, Path backupPath, String dataLabel) {
		Path restorePath = targetPath.resolveSibling(targetPath.getFileName() + ".restore");
		try {
			Files.copy(backupPath, restorePath, StandardCopyOption.REPLACE_EXISTING);
			moveReplacing(restorePath, targetPath);
		} catch (IOException exception) {
			try {
				Files.deleteIfExists(restorePath);
			} catch (IOException cleanupException) {
				exception.addSuppressed(cleanupException);
			}
			LOGGER.warn("Recovered {} from backup, but failed to restore {} from {}.",
				dataLabel, targetPath, backupPath, exception);
		}
	}

	private static void moveReplacing(Path sourcePath, Path targetPath) throws IOException {
		try {
			Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
