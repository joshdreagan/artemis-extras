package com.joshdreagan.artemis.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class ExternallyConfiguredPlugin implements ActiveMQServerPlugin {

  private static final Logger log = LoggerFactory.getLogger(ExternallyConfiguredPlugin.class);

  public static final String LOCATION = "location";

  private ActiveMQServer server;
  private URI location;

  private boolean initialized = false;

  @Override
  public void init(Map<String, String> properties) {
    if (properties == null || properties.isEmpty()) {
      throw new IllegalArgumentException("Plugin properties cannot be null or empty");
    }

    log.debug("Initializing plugin [{}] with properties: {}", this.getClass().getSimpleName(), properties);

    Map<String, String> usedProperties = new HashMap<>(properties);

    String rawLocation = Objects.requireNonNull(usedProperties.remove(LOCATION), String.format("%s property is required", LOCATION));

    if (!Pattern.matches("^\\S:(\\/\\/)?\\S+", rawLocation)) {
      String path = rawLocation.replaceFirst("^file:(\\/\\/)?", "");
      if (!path.startsWith("/")) {
        path = Paths.get(path).toAbsolutePath().toString();
      }
      rawLocation = "file://" + path;
    }
    try {
      location = new URI(rawLocation);
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to parse location URI: " + rawLocation, e);
    }

    if (!usedProperties.isEmpty()) {
      throw new IllegalArgumentException(String.format("Unknown properties: [%s]", String.join(",", usedProperties.keySet())));
    }

    initialized = true;
    log.debug("Initialized plugin: {}", toString());
  }

  @Override
  public void registered(ActiveMQServer server) {
    if (!initialized) {
      throw new IllegalStateException(String.format("%s not initialized", getClass().getSimpleName()));
    }

    String type = null;
    ObjectMapper mapper = null;
    if (location.getPath().endsWith(".json")) {
      type = "json";
    } else if (location.getPath().endsWith(".yaml")) {
      type = "yaml";
    } else {
      String query = location.getQuery();
      String[] queryParts = query.split("\\Q&\\E");
      if (queryParts != null && queryParts.length > 0) {
        Map<String, List<String>> queryParams = new HashMap<>();
        for (String queryPart : queryParts) {
          String[] keyValue = queryPart.split("\\Q=\\E");
          if (keyValue.length == 2) {
            queryParams.computeIfAbsent(keyValue[0], k -> new ArrayList<>()).add(keyValue[1]);
          }
        }
        type = queryParams.get("type").getFirst();
      }
    }
    mapper = switch (type.toLowerCase()) {
      case "json" -> new ObjectMapper();
      case "yaml" -> new ObjectMapper(YAMLFactory
        .builder()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        .build()
      );
      default -> null;
    };
    if (mapper == null) {
      throw new IllegalArgumentException(String.format("Invalid configuration file type: %s", type));
    }
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    try {
      Map<String, Object> rootPluginConfig = mapper.readValue(location.toURL(), Map.class);
      List<Map<String, Object>> pluginConfigs = (List<Map<String, Object>>) rootPluginConfig.get("plugins");
      if (pluginConfigs == null) {
        throw new IllegalArgumentException("Invalid configuration file: missing 'plugins' array");
      }
      for (Map<String, Object> pluginConfig : pluginConfigs) {
        String pluginClassName = (String) pluginConfig.get("class-name");
        Map<String, String> pluginProperties = (Map<String, String>) pluginConfig.get("properties");

        server.registerBrokerPlugin(initializePlugin(pluginClassName, pluginProperties));
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid configuration file", e);
    }

    this.server = server;
  }

  private ActiveMQServerBasePlugin initializePlugin(String pluginClassName, Map<String, String> pluginProperties) {
    try {
      Class<? extends ActiveMQServerBasePlugin> pluginClass = Class.forName(pluginClassName).asSubclass(ActiveMQServerBasePlugin.class);
      ActiveMQServerBasePlugin plugin = pluginClass.getConstructor().newInstance();
      plugin.init(pluginProperties);
      return plugin;
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to initialize plugin", e);
    }
  }

  @Override
  public void unregistered(ActiveMQServer server) {
    this.server = null;
  }
}
