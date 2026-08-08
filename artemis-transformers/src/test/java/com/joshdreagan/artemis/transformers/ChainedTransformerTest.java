package com.joshdreagan.artemis.transformers;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChainedTransformerTest {

  @BeforeEach
  void setUp() {
    RecordingTransformer.applied.clear();
  }

  @Test
  void rejectsMissingTransformerConfiguration() {
    ChainedTransformer transformer = new ChainedTransformer();

    assertThrows(IllegalArgumentException.class, () -> transformer.init(null));
    assertThrows(IllegalArgumentException.class, () -> transformer.init(Map.of()));
  }

  @Test
  void rejectsPropertiesWithoutTransformerName() {
    ChainedTransformer transformer = new ChainedTransformer();

    assertThrows(IllegalArgumentException.class,
      () -> transformer.init(Map.of("dummy-key", "dummy-value")));
  }

  @Test
  void rejectsTransformerWithoutClass() {
    ChainedTransformer transformer = new ChainedTransformer();

    assertThrows(IllegalArgumentException.class,
      () -> transformer.init(Map.of(ChainedTransformer.NAMES_PROPERTY, "valid-name")));
  }

  @Test
  void appliesTransformersInNameOrder() {
    ChainedTransformer transformer = new ChainedTransformer();
    Map<String, String> properties = new HashMap<>();
    List<String> names = new ArrayList<>();

    String firstTransformerName = "first-transformer";
    properties.putAll(
      ChainedTransformer.combine(
        firstTransformerName,
        new DelegateTransformerConfig(
          RecordingTransformer.class.getName(),
          Map.of("id", firstTransformerName)
        )
      )
    );
    names.add(firstTransformerName);

    String secondTransformerName = "second-transformer";
    properties.putAll(
      ChainedTransformer.combine(
        secondTransformerName,
        new DelegateTransformerConfig(
          RecordingTransformer.class.getName(),
          Map.of("id", secondTransformerName)
        )
      )
    );
    names.add(secondTransformerName);

    properties.put(ChainedTransformer.NAMES_PROPERTY, String.join(",", names));

    transformer.init(properties);
    Message result = transformer.transform(new CoreMessage());

    assertEquals(List.of(firstTransformerName, secondTransformerName), RecordingTransformer.applied);
  }

  @Test
  void stopsApplyingTransformersWhenOneReturnsNull() {
    ChainedTransformer transformer = new ChainedTransformer();
    Map<String, String> properties = new HashMap<>();
    List<String> names = new ArrayList<>();

    String firstTransformerName = "first-transformer";
    properties.putAll(
      ChainedTransformer.combine(
        firstTransformerName,
        new DelegateTransformerConfig(
          NullTransformer.class.getName(),
          null
        )
      )
    );
    names.add(firstTransformerName);

    String secondTransformerName = "second-transformer";
    properties.putAll(
      ChainedTransformer.combine(
        secondTransformerName,
        new DelegateTransformerConfig(
          RecordingTransformer.class.getName(),
          Map.of("id", secondTransformerName)
        )
      )
    );
    names.add(secondTransformerName);

    properties.put(ChainedTransformer.NAMES_PROPERTY, String.join(",", names));

    transformer.init(properties);

    assertNull(transformer.transform(new CoreMessage()));
    assertEquals(List.of(), RecordingTransformer.applied);
  }
}
