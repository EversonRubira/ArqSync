package com.arqsync.cli;

/**
 * Abstraction over {@link System#exit(int)} so the orchestrator's exit-code
 * decisions can be tested without killing the test JVM (SPEC-cli.md, 2.9).
 */
public interface ProcessExiter {

    void exit(int code);
}
