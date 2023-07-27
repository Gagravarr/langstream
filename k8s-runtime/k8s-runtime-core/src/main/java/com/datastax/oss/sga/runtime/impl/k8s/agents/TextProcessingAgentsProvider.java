package com.datastax.oss.sga.runtime.impl.k8s.agents;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

import static com.datastax.oss.sga.runtime.impl.k8s.KubernetesClusterRuntime.CLUSTER_TYPE;

/**
 * Implements support for Text Processing Agents.
 */
@Slf4j
public class TextProcessingAgentsProvider extends AbstractComposableAgentProvider {

    private static final Set<String> SUPPORTED_AGENT_TYPES = Set.of("text-extractor",
            "language-detector",
            "text-splitter",
            "text-normaliser");

    public TextProcessingAgentsProvider() {
        super(SUPPORTED_AGENT_TYPES, List.of(CLUSTER_TYPE, "none"));
    }
}
