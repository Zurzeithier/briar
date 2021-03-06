package org.briarproject.api;

public interface AuthorFactory {

	Author createAuthor(String name, byte[] publicKey);

	LocalAuthor createLocalAuthor(String name, byte[] publicKey,
			byte[] privateKey);
}
