package com.arqsync.suggest;

/**
 * The kind of AI-generated suggestion. Declaration order is the fixed
 * priority used to sort suggestions before display: cycles first (highest
 * severity), then layer violations, then architectural style migration,
 * then anything else.
 */
public enum SuggestionType {
    CYCLE_BREAK,
    LAYER_VIOLATION,
    STYLE_MIGRATION,
    GENERAL
}
