package fr.maif.izanami.spring.service;

import fr.maif.features.results.IzanamiResult;

import java.util.Map;

/**
 * Result wrapper that includes both the Izanami result and metadata about the evaluation.
 *
 * @param result   the Izanami evaluation result
 * @param metadata metadata about the flag configuration and evaluation source
 */
public record ResultWithMetadata(
    IzanamiResult.Result result,
    Map<String, String> metadata
) {}

/*
TODO
public record ResultValueWithMetadata<T>(
    T value,
    Map<String, String> metadata
) {}
 */
