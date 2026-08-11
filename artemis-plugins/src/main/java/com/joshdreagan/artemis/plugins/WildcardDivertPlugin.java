package com.joshdreagan.artemis.plugins;

import com.joshdreagan.artemis.transformers.ChainedTransformer;
import com.joshdreagan.artemis.transformers.DelegateTransformerConfig;
import com.joshdreagan.artemis.transformers.DivertHeadersTransformer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

public class WildcardDivertPlugin implements ActiveMQServerPlugin {

  private static final Logger log = LoggerFactory.getLogger(WildcardDivertPlugin.class);

  public static final String DIVERT_PREFIX = "divert-prefix";
  public static final String ADDRESS_INCLUDES = "address-includes";
  public static final String ADDRESS_EXCLUDES = "address-excludes";
  public static final String FORWARDING_ADDRESS = "forwarding-address";
  public static final String ROUTING_TYPE = "routing-type";
  public static final String FILTER_STRING = "filter-string";
  public static final String EXCLUSIVE = "exclusive";
  public static final String TRANSFORMER_CLASS_NAME = "transformer-class-name";
  public static final String TRANSFORMER_PROPERTY_PREFIX = "transformer-property";

  private ActiveMQServer server;
  private WildcardConfiguration wildcardConfiguration;

  private String divertPrefix;
  private Set<String> addressIncludes;
  private Set<String> addressExcludes;
  private String forwardingAddress;
  private String routingType;
  private String filterString;
  private boolean exclusive;
  private String transformerClass;
  private Map<String, String> transformerProperties;

  private List<Pattern> includeRegexes;
  private List<Pattern> excludeRegexes;

  private boolean initialized = false;

  @Override
  public void init(Map<String, String> properties) {
    if (properties == null || properties.isEmpty()) {
      throw new IllegalArgumentException("Plugin properties cannot be null or empty");
    }

    log.debug("Initializing plugin [{}] with properties: {}", this.getClass().getSimpleName(), properties);

    Map<String, String> usedProperties = new HashMap<>(properties);

    divertPrefix = Objects.requireNonNull(usedProperties.remove(DIVERT_PREFIX), String.format("%s property is required", DIVERT_PREFIX));

    addressIncludes = new HashSet<>();
    String rawAddressIncludes = Objects.requireNonNull(usedProperties.remove(ADDRESS_INCLUDES), String.format("%s property is required", ADDRESS_INCLUDES));
    addressIncludes.addAll(Set.of(rawAddressIncludes.split("\\s*[,|]\\s*")));

    addressExcludes = new HashSet<>();
    String rawAddressExcludes = usedProperties.remove(ADDRESS_EXCLUDES);
    if (rawAddressExcludes != null) {
      addressExcludes.addAll(Set.of(rawAddressExcludes.split("\\s*[,|]\\s*")));
    }

    forwardingAddress = Objects.requireNonNull(usedProperties.remove(FORWARDING_ADDRESS), String.format("%s property is required", FORWARDING_ADDRESS));
    addressExcludes.add(forwardingAddress);

    routingType = usedProperties.remove(ROUTING_TYPE);
    if (routingType == null || routingType.isBlank()) {
      routingType = "STRIP";
    } else {
      routingType = routingType.toUpperCase();
    }

    filterString = usedProperties.remove(FILTER_STRING);

    String rawExclusive = usedProperties.remove(EXCLUSIVE);
    if (rawExclusive == null || rawExclusive.isBlank()) {
      exclusive = false;
    } else {
      exclusive = Boolean.parseBoolean(rawExclusive.toLowerCase());
    }

    String chainedTransformerClass = ChainedTransformer.class.getName();
    List<String> chainedTransformerNames = new ArrayList<>();

    String divertHeadersTransformerName = "divert-headers-transformer";
    String divertHeadersTransformerClassName = DivertHeadersTransformer.class.getName();
    Map<String, String> chainedTransformerProperties = new HashMap<>(ChainedTransformer.combine(
      divertHeadersTransformerName,
      new DelegateTransformerConfig(
        divertHeadersTransformerClassName,
        null
      )
    ));
    chainedTransformerNames.add(divertHeadersTransformerName);

    String delegateTransformerName = "delegate-transformer";
    String delegateTransformerClassName = usedProperties.remove(TRANSFORMER_CLASS_NAME);
    if (delegateTransformerClassName != null) {
      Map<String, String> delegateTransformerProperties = new HashMap<>();
      properties.forEach((key, value) -> {
        String prefix = TRANSFORMER_PROPERTY_PREFIX + ".";
        if (key.startsWith(prefix)) {
          String strippedKey = key.replaceFirst("^\\Q" + prefix + "\\E", "");
          delegateTransformerProperties.put(strippedKey, value);
          usedProperties.remove(key);
        }
      });
      chainedTransformerProperties.putAll(
        ChainedTransformer.combine(
          delegateTransformerName,
          new DelegateTransformerConfig(
            delegateTransformerClassName,
            delegateTransformerProperties
          )
        )
      );
      chainedTransformerNames.add(delegateTransformerName);
    }

    chainedTransformerProperties.put(ChainedTransformer.NAMES_PROPERTY, String.join(", ", chainedTransformerNames));

    transformerClass = chainedTransformerClass;
    transformerProperties = chainedTransformerProperties;

    if (!usedProperties.isEmpty()) {
      throw new IllegalArgumentException(String.format("Unknown properties: [%s]", String.join(",", usedProperties.keySet())));
    }

    initialized = true;
    log.debug("Initialized plugin: {}", this);
  }

  @Override
  public void registered(ActiveMQServer server) {
    if (!initialized) {
      throw new IllegalStateException(String.format("%s not initialized", getClass().getSimpleName()));
    }

    this.server = server;
    wildcardConfiguration = server.getConfiguration().getWildcardConfiguration();

    includeRegexes = new ArrayList<>();
    for (String addressInclude : addressIncludes) {
      includeRegexes.add(Pattern.compile(Helper.wildcardToRegex(addressInclude, wildcardConfiguration)));
    }

    excludeRegexes = new ArrayList<>();
    for (String addressExclude : addressExcludes) {
      excludeRegexes.add(Pattern.compile(Helper.wildcardToRegex(addressExclude, wildcardConfiguration)));
    }

    log.debug("Plugin registered: {}", this);
  }

  @Override
  public void unregistered(ActiveMQServer server) {
    this.server = null;
    wildcardConfiguration = null;

    includeRegexes.clear();
    includeRegexes = null;

    excludeRegexes.clear();
    excludeRegexes = null;

    log.debug("Plugin unregistered: {}", this);
  }

  @Override
  public void afterAddAddress(AddressInfo addressInfo, boolean reload) throws ActiveMQException {
    if (!initialized) {
      throw new IllegalStateException(String.format("%s not initialized", getClass().getSimpleName()));
    }

    String createdAddress = addressInfo.getName().toString();

    for (Pattern excludeRegex : excludeRegexes) {
      if (excludeRegex.matcher(createdAddress).matches()) {
        return;
      }
    }

    for (Pattern includeRegex : includeRegexes) {
      if (includeRegex.matcher(createdAddress).matches()) {
        DivertConfiguration divertConfiguration = new DivertConfiguration()
          .setName(divertPrefix + createdAddress)
          .setAddress(createdAddress)
          .setForwardingAddress(forwardingAddress)
          .setRoutingType(ComponentConfigurationRoutingType.valueOf(routingType))
          .setFilterString(filterString)
          .setExclusive(exclusive);
        if (transformerClass != null && !transformerClass.isBlank()) {
          TransformerConfiguration transformerConfiguration = new TransformerConfiguration();
          transformerConfiguration.setClassName(transformerClass);
          transformerConfiguration.setProperties(transformerProperties);
          divertConfiguration.setTransformerConfiguration(transformerConfiguration);
        }
        try {
          server.deployDivert(divertConfiguration);
          log.debug("Divert deployed: {}", divertConfiguration);
        } catch (Exception e) {
          throw new ActiveMQException("Unable to deploy divert for address: " + createdAddress, e);
        }
      }
      break;
    }
  }

  @Override
  public String toString() {
    ToStringBuilder tsb = new ToStringBuilder(this);
    tsb.append("divertPrefix", divertPrefix);
    tsb.append("addressIncludes", addressIncludes);
    tsb.append("addressExcludes", addressExcludes);
    tsb.append("forwardingAddress", forwardingAddress);
    tsb.append("routingType", routingType);
    tsb.append("filterString", filterString);
    tsb.append("exclusive", exclusive);
    tsb.append("transformerClass", transformerClass);
    tsb.append("transformerProperties", transformerProperties);
    return tsb.toString();
  }
}
