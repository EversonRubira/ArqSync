package com.arqsync.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitRepositoryResolverTest {

    private final GitRepositoryResolver resolver = new GitRepositoryResolver();

    @Test
    void isGitUrlRecognizesHttpAndHttpsArguments() {
        assertThat(GitRepositoryResolver.isGitUrl("https://github.com/user/repo.git")).isTrue();
        assertThat(GitRepositoryResolver.isGitUrl("http://gitlab.com/user/repo.git")).isTrue();
    }

    @Test
    void isGitUrlRejectsLocalPaths() {
        assertThat(GitRepositoryResolver.isGitUrl("/home/user/project")).isFalse();
        assertThat(GitRepositoryResolver.isGitUrl("./relative/project")).isFalse();
        assertThat(GitRepositoryResolver.isGitUrl("C:\\projects\\my-app")).isFalse();
    }

    @Test
    void resolveThrowsOnAMalformedUrl() {
        assertThatThrownBy(() -> resolver.resolve("not a url"))
                .isInstanceOf(GitCloneException.class)
                .hasMessageContaining("Invalid repository URL");
    }

    @Test
    void resolveThrowsWhenSchemeIsNotHttpOrHttps() {
        assertThatThrownBy(() -> resolver.resolve("ftp://example.com/repo.git"))
                .isInstanceOf(GitCloneException.class)
                .hasMessageContaining("Invalid repository URL");
    }

    @Test
    void resolveThrowsWhenUrlHasNoHost() {
        assertThatThrownBy(() -> resolver.resolve("https:///no-host"))
                .isInstanceOf(GitCloneException.class)
                .hasMessageContaining("Invalid repository URL");
    }

    @Test
    void enforceSizeLimitPassesWhenDirectoryIsWithinTheLimit(@TempDir Path dir) throws IOException {
        writeSparseFile(dir.resolve("small.bin"), 50);

        GitRepositoryResolver.enforceSizeLimit(dir, 100);
    }

    @Test
    void enforceSizeLimitThrowsWhenDirectoryExceedsTheLimit(@TempDir Path dir) throws IOException {
        writeSparseFile(dir.resolve("big.bin"), 200);

        assertThatThrownBy(() -> GitRepositoryResolver.enforceSizeLimit(dir, 100))
                .isInstanceOf(GitCloneException.class)
                .hasMessageContaining("exceeds the maximum allowed size");
    }

    @Test
    void enforceJavaFileCountLimitPassesWhenWithinTheLimit(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("A.java"));
        Files.createFile(dir.resolve("B.java"));

        GitRepositoryResolver.enforceJavaFileCountLimit(dir, 2);
    }

    @Test
    void enforceJavaFileCountLimitThrowsWhenExceedingTheLimit(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("A.java"));
        Files.createFile(dir.resolve("B.java"));
        Files.createFile(dir.resolve("C.java"));

        assertThatThrownBy(() -> GitRepositoryResolver.enforceJavaFileCountLimit(dir, 2))
                .isInstanceOf(GitCloneException.class)
                .hasMessageContaining("exceeds the maximum allowed number of .java files");
    }

    @Test
    void enforceJavaFileCountLimitIgnoresNonJavaFiles(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("README.md"));
        Files.createFile(dir.resolve("A.java"));

        GitRepositoryResolver.enforceJavaFileCountLimit(dir, 1);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void resolveThrowsAndCleansUpWhenCloneTimesOut() throws IOException, InterruptedException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            Thread neverRespondingServer = new Thread(() -> {
                try (Socket ignored = serverSocket.accept()) {
                    Thread.sleep(10_000);
                } catch (Exception ignored) {
                    // test ends before the server thread would ever respond
                }
            });
            neverRespondingServer.setDaemon(true);
            neverRespondingServer.start();

            GitRepositoryResolver shortTimeoutResolver = new GitRepositoryResolver(1);
            String url = "http://localhost:" + port + "/repo.git";

            long javaTempDirEntriesBefore = countArqsyncCloneTempDirs();

            assertThatThrownBy(() -> shortTimeoutResolver.resolve(url))
                    .isInstanceOf(GitCloneException.class);

            assertThat(countArqsyncCloneTempDirs())
                    .as("temporary clone directory must be cleaned up after a failed clone")
                    .isEqualTo(javaTempDirEntriesBefore);
        }
    }

    private long countArqsyncCloneTempDirs() throws IOException {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        try (var entries = Files.list(tmp)) {
            return entries.filter(p -> p.getFileName().toString().startsWith("arqsync-clone-")).count();
        }
    }

    private void writeSparseFile(Path file, long sizeInBytes) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(
                file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(sizeInBytes - 1);
            channel.write(ByteBuffer.wrap(new byte[]{1}));
        }
    }
}
