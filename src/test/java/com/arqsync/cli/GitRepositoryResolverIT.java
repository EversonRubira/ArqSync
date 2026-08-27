package com.arqsync.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link GitRepositoryResolver} against a real public repository
 * over the network (github.com/octocat/Hello-World, a well-known tiny
 * fixture repo maintained by GitHub itself).
 *
 * Named *IT (not *Test) per SPEC-testing.md's Surefire/Failsafe convention:
 * runs under {@code ./mvnw verify}, not {@code ./mvnw test}, since it
 * requires outbound network access, unlike the rest of the CLI test suite.
 */
class GitRepositoryResolverIT {

    private static final String VALID_PUBLIC_REPOSITORY_URL = "https://github.com/octocat/Hello-World.git";

    private final GitRepositoryResolver resolver = new GitRepositoryResolver();

    @Test
    void resolvesAValidPublicRepositoryUrlIntoAClonedDirectory() {
        Path clonedDir = resolver.resolve(VALID_PUBLIC_REPOSITORY_URL);

        try {
            assertThat(clonedDir).exists().isDirectory();
            assertThat(clonedDir.resolve(".git")).exists();
        } finally {
            GitRepositoryResolver.deleteRecursively(clonedDir);
        }

        assertThat(clonedDir).doesNotExist();
    }

    @Test
    void resolveFailsFatallyForAPrivateOrNonExistentRepository() {
        try {
            resolver.resolve("https://github.com/octocat/this-repository-does-not-exist-arqsync-test.git");
            throw new AssertionError("Expected GitCloneException for a non-existent repository");
        } catch (GitCloneException expected) {
            assertThat(expected.getMessage()).isNotBlank();
        }
    }
}
