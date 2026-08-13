package com.joshdreagan.artemis.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactoryBuilder;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;

public class ExternallyConfiguredPlugin implements ActiveMQServerPlugin {

  private static final Logger log = LoggerFactory.getLogger(ExternallyConfiguredPlugin.class);

  public static final String LOCATION = "location";

  private final List<ActiveMQServerBasePlugin> plugins = new ArrayList<>();
  private boolean initialized = false;

  private URL location;

  @Override
  public void init(Map<String, String> properties) {
    if (properties == null || properties.isEmpty()) {
      throw new IllegalArgumentException("Plugin properties cannot be null or empty");
    }

    log.debug("Initializing plugin [{}] with properties: {}", this.getClass().getSimpleName(), properties);

    Map<String, String> usedProperties = new HashMap<>(properties);

    String rawLocation = Objects.requireNonNull(usedProperties.remove(LOCATION), String.format("%s property is required", LOCATION));
    if (!rawLocation.matches("^\\S:(//)?\\S+") || rawLocation.startsWith("file:")) {
      String path = rawLocation.replaceFirst("^file:(//)?", "");
      if (!path.startsWith("/")) {
        path = Paths.get(path).toAbsolutePath().toString();
      }
      rawLocation = "file://" + path;
    }
    try {
      location = URI.create(rawLocation).toURL();
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to parse location URL: " + rawLocation, e);
    }

    if (!usedProperties.isEmpty()) {
      throw new IllegalArgumentException(String.format("Unknown properties: [%s]", String.join(",", usedProperties.keySet())));
    }

    Map<String, Object> config;
    try {
      config = parseConfigurationFile(location);
      if (config == null || config.isEmpty()) {
        throw new IllegalArgumentException("Parsed configuration is empty or null: " + location);
      }
    } catch (IOException e) {
      throw new RuntimeException("Unable to parse configuration file: " + location, e);
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> pluginConfigs = (List<Map<String, Object>>) config.get("plugins");
    if (pluginConfigs == null) {
      throw new IllegalArgumentException("Configuration file does not contain a 'plugins' list");
    }
    for (Map<String, Object> pluginConfig : pluginConfigs) {
      String pluginClass = (String) pluginConfig.get("class-name");
      if (pluginClass == null) {
        throw new IllegalArgumentException("Plugin configuration does not contain a 'class-name' property");
      }
      @SuppressWarnings("unchecked")
      Map<String, String> pluginProperties = (Map<String, String>) pluginConfig.get("properties");
      plugins.add(initializePlugin(pluginClass, pluginProperties));
    }

    initialized = true;
    log.debug("Initialized plugin: {}", this);
  }

  protected static Map<String, Object> parseConfigurationFile(URL location) throws IOException {
    String type = Helper.getConfigType(location);
    return switch (type.toLowerCase()) {
      case "json" -> parseJsonConfigurationFile(location);
      case "yaml" -> parseYamlConfigurationFile(location);
      case "xml" -> parseXmlConfigurationFile(location);
      default -> throw new IllegalArgumentException(String.format("Invalid configuration file type: %s", type));
    };
  }

  @SuppressWarnings("unchecked")
  protected static Map<String, Object> parseJsonConfigurationFile(URL location) {
    ObjectMapper mapper = new ObjectMapper();
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    try (InputStream is = location.openStream()) {
      return mapper.readValue(is, Map.class);
    } catch (IOException e) {
      throw new RuntimeException("Unable to parse configuration file: " + location, e);
    }
  }

  @SuppressWarnings("unchecked")
  protected static Map<String, Object> parseYamlConfigurationFile(URL location) {
    YAMLFactoryBuilder builder = YAMLFactory.builder();
    builder.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
    ObjectMapper mapper = new ObjectMapper(builder.build());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    try (InputStream is = location.openStream()) {
      return mapper.readValue(is, Map.class);
    } catch (IOException e) {
      throw new RuntimeException("Unable to parse configuration file: " + location, e);
    }
  }

  @SuppressWarnings("unchecked")
  protected static Map<String, Object> parseXmlConfigurationFile(URL location) {
    ObjectMapper mapper = new XmlMapper();
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    Map<String, Object> rawConfig;
    try (InputStream is = location.openStream()) {
      rawConfig = mapper.readValue(is, Map.class);
      if (rawConfig == null) {
        return null;
      }
    } catch (IOException e) {
      throw new RuntimeException("Unable to parse configuration file: " + location, e);
    }

    Map<String, Object> formattedConfig = new HashMap<>();
    List<Map<String, Object>> formattedPlugins = (List<Map<String, Object>>) formattedConfig.computeIfAbsent("plugins", k -> new ArrayList<>());

    List<Map<String, Object>> rawPlugins = (List<Map<String, Object>>) rawConfig.get("plugin");
    if (rawPlugins == null) {
      return formattedConfig;
    }

    rawPlugins.forEach(rawPlugin -> {
      Map<String, Object> formattedPlugin = new HashMap<>();

      String rawClassName = (String) rawPlugin.get("class-name");
      formattedPlugin.put("class-name", rawClassName);

      Map<String, Object> rawProperties = (Map<String, Object>) rawPlugin.get("properties");
      if (rawProperties != null) {
        Map<String, Object> formattedProperties = new HashMap<>();

        List<Map<String, Object>> rawPropertyItems = (List<Map<String, Object>>) rawProperties.get("property");
        if (rawPropertyItems != null) {
          rawPropertyItems.forEach(rawPropertyItem -> {
            String rawPropertyName = (String) rawPropertyItem.getOrDefault("key", rawPropertyItem.get("name"));
            String rawPropertyValue = (String) rawPropertyItem.getOrDefault("value", rawPropertyItem.get(""));
            formattedProperties.put(rawPropertyName, rawPropertyValue);
          });
        }

        formattedPlugin.put("properties", formattedProperties);
      }

      formattedPlugins.add(formattedPlugin);
    });
    return formattedConfig;
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
  public void registered(ActiveMQServer server) {
    if (!initialized) {
      throw new IllegalStateException(String.format("%s not initialized", getClass().getSimpleName()));
    }

    for (ActiveMQServerBasePlugin plugin : plugins) {
      server.registerBrokerPlugin(plugin);
    }
  }

  @Override
  public void unregistered(ActiveMQServer server) {
    for (ActiveMQServerBasePlugin plugin : plugins) {
      server.unRegisterBrokerPlugin(plugin);
    }
  }

  @Override
  public String toString() {
    ToStringBuilder tsb = new ToStringBuilder(this);
    tsb.append("location", location);
    return tsb.toString();
  }
}
