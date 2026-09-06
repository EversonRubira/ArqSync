package com.arqsync.analyzer;

/**
 * The architectural role of a package under a style-specific classifier
 * (ADENDO-SPEC-analyzer-classificador-papel.md) — e.g. for Hexagonal, whether
 * a package holds core/port interfaces or adapter implementations.
 * {@code UNKNOWN} means the classifier found no signal for that package, the
 * same "not a miss" philosophy as {@link ArchitectureStyle}'s own UNKNOWN
 * (SPEC-analyzer.md, 2.4).
 *
 * <p>{@code INPUT_PORT} (convention: {@code usecase}/{@code usecases}) and
 * {@code OUTPUT_PORT} (convention: {@code port}/{@code ports}) replace the original
 * single {@code CORE} role — a driving adapter depends on an input port, a driven
 * adapter implements an output port, and the two are not interchangeable
 * (ADENDO-SPEC-analyzer-adapter-porta-direcao.md, 2.3–2.4). {@code DRIVING_ADAPTER}
 * (convention: an {@code adapter}/{@code adapters} segment plus {@code in}/
 * {@code driving}) and {@code DRIVEN_ADAPTER} (plus {@code out}/{@code driven})
 * refine {@code ADAPTER} the same way — {@code ADAPTER} is kept as the fallback for
 * an adapter package whose direction couldn't be determined (same adendo, 2.1–2.2),
 * not removed, since the future concept diagram only needs "is this an adapter" and
 * shouldn't break on a distinction it doesn't consume.
 */
public enum PackageRole {
    INPUT_PORT,
    OUTPUT_PORT,
    ADAPTER,
    DRIVING_ADAPTER,
    DRIVEN_ADAPTER,
    UNKNOWN
}
