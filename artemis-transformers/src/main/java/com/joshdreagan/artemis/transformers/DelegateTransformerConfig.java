package com.joshdreagan.artemis.transformers;

import java.util.Map;
import java.util.Objects;

public record DelegateTransformerConfig(String transformerClass,
                                        Map<String, String> transformerProperties) {
  public DelegateTransformerConfig {
    Objects.requireNonNull(transformerClass, "Parameter transformerClass cannot be null");
  }
}
