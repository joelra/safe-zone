package com.simpleforapanda.safezone.manager;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ClaimIdGenerator {
	private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
	private static final int ID_LENGTH = 6;
	private static final int MAX_ATTEMPTS = 64;

	private final Supplier<String> candidateSupplier;

	public ClaimIdGenerator() {
		this(ClaimIdGenerator::randomClaimId);
	}

	public ClaimIdGenerator(Supplier<String> candidateSupplier) {
		this.candidateSupplier = Objects.requireNonNull(candidateSupplier, "candidateSupplier");
	}

	public String generateUniqueId(Predicate<String> alreadyExists) {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String candidate = this.candidateSupplier.get();
			if (!alreadyExists.test(candidate)) {
				return candidate;
			}
		}

		throw new IllegalStateException("Unable to generate a unique claim id");
	}

	private static String randomClaimId() {
		char[] chars = new char[ID_LENGTH];
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int index = 0; index < chars.length; index++) {
			chars[index] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
		}
		return new String(chars);
	}
}
