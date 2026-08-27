package com.arqsync.cli;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Resolves a Git repository URL argument into a local directory to analyze,
 * by cloning it with JGit into a temporary directory (SPEC-cli.md, "Entrada
 * via URL"). Only public repositories are supported in the v1; clones larger
 * than {@link #MAX_REPOSITORY_SIZE_BYTES} or with more than
 * {@link #MAX_JAVA_FILE_COUNT} {@code .java} files are rejected and cleaned
 * up.
 */
@Component
public class GitRepositoryResolver {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryResolver.class);

    static final long MAX_REPOSITORY_SIZE_BYTES = 100L * 1024 * 1024;
    static final int MAX_JAVA_FILE_COUNT = 10_000;
    private static final int CLONE_TIMEOUT_SECONDS = 5 * 60;

    private final int cloneTimeoutSeconds;

    public GitRepositoryResolver() {
        this(CLONE_TIMEOUT_SECONDS);
    }

    /**
     * Package-private: lets tests use a short clone timeout to deterministically
     * exercise the "clone times out" scenario, instead of waiting on the real
     * 5-minute production timeout.
     */
    GitRepositoryResolver(int cloneTimeoutSeconds) {
        this.cloneTimeoutSeconds = cloneTimeoutSeconds;
    }

    /**
     * True when the argument should be treated as a Git URL rather than a
     * local filesystem path (SPEC-cli.md, "Entrada via URL", item 1).
     */
    public static boolean isGitUrl(String argument) {
        return argument.startsWith("http://") || argument.startsWith("https://");
    }

    /**
     * Clones {@code url} into a fresh temporary directory and returns its
     * path. The temporary directory is deleted before this method returns
     * abnormally (any thrown exception); the caller owns cleanup of the
     * returned directory on the success path.
     *
     * @throws GitCloneException if the URL is malformed, the repository is
     *                           private/unreachable, the clone times out, or
     *                           it exceeds the v1 size/file-count limits
     */
    public Path resolve(String url) {
        validateUrl(url);

        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("arqsync-clone-");
        } catch (IOException e) {
            throw new GitCloneException("Failed to create a temporary directory for the clone: " + e.getMessage(), e);
        }

        try {
            log.info("Cloning repository from {}...", url);
            cloneInto(url, tempDir);
            log.info("Repository cloned to {}", tempDir);

            enforceSizeLimit(tempDir, MAX_REPOSITORY_SIZE_BYTES);
            enforceJavaFileCountLimit(tempDir, MAX_JAVA_FILE_COUNT);

            return tempDir;
        } catch (RuntimeException e) {
            deleteRecursively(tempDir);
            throw e;
        }
    }

    private void validateUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new GitCloneException("Invalid repository URL: " + url);
        }
        boolean hasHttpScheme = uri.getScheme() != null
                && (uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"));
        if (!hasHttpScheme || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new GitCloneException("Invalid repository URL: " + url);
        }
    }

    private void cloneInto(String url, Path tempDir) {
        try (Git ignored = Git.cloneRepository()
                .setURI(url)
                .setDirectory(tempDir.toFile())
                .setTimeout(cloneTimeoutSeconds)
                .call()) {
            // clone completed; the Git handle only needs closing, nothing else to do here.
        } catch (InvalidRemoteException e) {
            throw new GitCloneException("Invalid repository URL: " + url, e);
        } catch (TransportException e) {
            throw new GitCloneException(describeTransportFailure(url, e), e);
        } catch (GitAPIException e) {
            throw new GitCloneException("Failed to clone repository '" + url + "': " + e.getMessage(), e);
        }
    }

    private String describeTransportFailure(String url, TransportException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("not authorized") || message.contains("authentication") || message.contains("403")) {
            return "Repository is not accessible - only public repositories are supported in v1: " + url;
        }
        if (message.contains("timed out") || message.contains("timeout")) {
            return "Cloning repository timed out: " + url;
        }
        return "Failed to reach repository (network unavailable): " + url;
    }

    static void enforceSizeLimit(Path dir, long maxBytes) {
        long size = directorySize(dir);
        if (size > maxBytes) {
            throw new GitCloneException(
                    "Repository exceeds the maximum allowed size of " + megabytes(maxBytes)
                            + " (cloned size: " + megabytes(size) + ")");
        }
    }

    static void enforceJavaFileCountLimit(Path dir, int maxFiles) {
        long javaFileCount = countJavaFiles(dir);
        if (javaFileCount > maxFiles) {
            throw new GitCloneException(
                    "Repository exceeds the maximum allowed number of .java files ("
                            + maxFiles + "): found " + javaFileCount);
        }
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1fMB", bytes / (1024.0 * 1024));
    }

    private static long directorySize(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile)
                    .mapToLong(GitRepositoryResolver::sizeOrZero)
                    .sum();
        } catch (IOException e) {
            throw new GitCloneException("Failed to inspect cloned repository size: " + e.getMessage(), e);
        }
    }

    private static long sizeOrZero(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long countJavaFiles(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .count();
        } catch (IOException e) {
            throw new GitCloneException("Failed to inspect cloned repository contents: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a directory and all of its contents, best-effort. Used both
     * internally (a clone that fails validation) and by the caller once the
     * analysis of a cloned repository is done (unless {@code --keep} was
     * passed).
     */
    public static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(GitRepositoryResolver::deleteQuietly);
        } catch (IOException e) {
            log.warn("Failed to clean up temporary directory {}: {}", dir, e.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            log.warn("Failed to delete {}: {}", path, e.getMessage());
        }
    }
}
