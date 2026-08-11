package com.joshdreagan.artemis.transformers;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ChainedTransformer implements Transformer {

  private static final Logger log = LoggerFactory.getLogger(ChainedTransformer.class);

  public static final String NAMES_PROPERTY = "names";
  public static final String CLASS_PROPERTY = "transformer-class-name";

  private static final String DELEGATE_PREFIX_FORMAT = "delegate.%s";
  private static final String CLASS_NAME_FORMAT = DELEGATE_PREFIX_FORMAT + "." + ChainedTransformer.CLASS_PROPERTY;
  private static final String PROPERTY_FORMAT = DELEGATE_PREFIX_FORMAT + ".properties.%s";

  private List<Transformer> transformers = new ArrayList<>();

  private boolean initialized = false;

  @Override
  public void init(Map<String, String> properties) {
    if (properties == null || properties.isEmpty()) {
      throw new IllegalArgumentException("Transformer properties cannot be null or empty");
    }

    log.debug("Initializing transformer [{}] with properties: {}", this.getClass().getSimpleName(), properties);

    String rawNames = properties.get(ChainedTransformer.NAMES_PROPERTY);
    if (rawNames == null || rawNames.isEmpty()) {
      throw new IllegalArgumentException("Missing list of delegate transformer names");
    }
    String[] names = rawNames.split("\\s*,\\s*");
    for (String name : names) {
      DelegateTransformerConfig delegate = separate(name.trim(), properties);
      if (delegate == null) {
        throw new IllegalArgumentException("Transformer configuration not found for: " + name);
      }
      transformers.add(instantiateTransformer(name, delegate));
    }

    initialized = true;
    log.debug("Initialized transformer: {}", toString());
  }

  @Override
  public Message transform(Message message) {
    if (!initialized) {
      throw new IllegalStateException(String.format("%s not initialized", getClass().getSimpleName()));
    }

    Message result = message;
    for (Transformer transformer : transformers) {
      if (result == null) {
        break;
      }
      result = transformer.transform(result);
    }
    return result;
  }

  private static Transformer instantiateTransformer(String transformerName, DelegateTransformerConfig transformerConfig) {
    try {
      Class<? extends Transformer> transformerClass = Class.forName(transformerConfig.transformerClass()).asSubclass(Transformer.class);
      Transformer transformer = transformerClass.getDeclaredConstructor().newInstance();
      transformer.init(transformerConfig.transformerProperties());
      return transformer;
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to initialize transformer: " + transformerName, e);
    }
  }

  public static String wrapDelegateTransformerClassNameKey(String transformerName) {
    Objects.requireNonNull(transformerName, "Parameter transformerName cannot be null");

    return String.format(CLASS_NAME_FORMAT, transformerName);
  }

  public static String unwrapDelegateTransformerClassNameKey(String transformerName, String transformerPropertyKey) {
    Objects.requireNonNull(transformerName, "Parameter transformerName cannot be null");
    Objects.requireNonNull(transformerPropertyKey, "Parameter transformerPropertyKey cannot be null");

    String prefix = String.format(DELEGATE_PREFIX_FORMAT + ".", transformerName);
    return transformerPropertyKey.replaceFirst("^\\Q" + prefix + "\\E", "");
  }

  public static String wrapDelegateTransformerPropertyKey(String transformerName, String transformerPropertyKey) {
    Objects.requireNonNull(transformerName, "Parameter transformerName cannot be null");
    Objects.requireNonNull(transformerPropertyKey, "Parameter transformerPropertyKey cannot be null");

    return String.format(PROPERTY_FORMAT, transformerName, transformerPropertyKey);
  }

  public static String unwrapDelegateTransformerPropertyKey(String transformerName, String transformerPropertyKey) {
    Objects.requireNonNull(transformerName, "Parameter transformerName cannot be null");
    Objects.requireNonNull(transformerPropertyKey, "Parameter transformerPropertyKey cannot be null");

    String prefix = String.format(DELEGATE_PREFIX_FORMAT + ".properties.", transformerName);
    return transformerPropertyKey.replaceFirst("^\\Q" + prefix + "\\E", "");
  }

  public static Map<String, String> combine(String transformerName, DelegateTransformerConfig transformerConfig) {
    Objects.requireNonNull(transformerName, "Parameter transformerName cannot be null");
    Objects.requireNonNull(transformerConfig, "Parameter transformerConfig cannot be null");

    Map<String, String> namedTransformerProperties = new HashMap<>();
    namedTransformerProperties.put(wrapDelegateTransformerClassNameKey(transformerName), transformerConfig.transformerClass());
    if (transformerConfig.transformerProperties() != null) {
      transformerConfig.transformerProperties().forEach((k, v) -> {
        namedTransformerProperties.put(wrapDelegateTransformerPropertyKey(transformerName, k), v);
      });
    }
    return namedTransformerProperties;
  }

  public static DelegateTransformerConfig separate(String transformerName, Map<String, String> combinedProperties) {
    Objects.requireNonNull(transformerName, "Parameter transformerName cannot be null");
    Objects.requireNonNull(combinedProperties, "Parameter combinedProperties cannot be null");

    Map<String, String> parsedTransformerProperties = new HashMap<>();
    String prefix = String.format(DELEGATE_PREFIX_FORMAT + ".", transformerName);
    String parsedTransformerClassName = null;
    for (Map.Entry<String, String> entry : combinedProperties.entrySet()) {
      if (!entry.getKey().startsWith(prefix)) {
        continue;
      }
      if (entry.getKey().equals(wrapDelegateTransformerClassNameKey(transformerName))) {
        parsedTransformerClassName = entry.getValue();
        continue;
      }
      parsedTransformerProperties.put(unwrapDelegateTransformerPropertyKey(transformerName, entry.getKey()), entry.getValue());
    }
    if (parsedTransformerClassName == null) {
      throw new IllegalArgumentException("Missing transformer class for transformer " + transformerName);
    }

    return new DelegateTransformerConfig(parsedTransformerClassName, parsedTransformerProperties);
  }

  @Override
  public String toString() {
    ToStringBuilder tsb = new ToStringBuilder(this);
    tsb.append("transformers", transformers);
    return tsb.toString();
  }
}
