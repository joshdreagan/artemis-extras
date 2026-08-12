package com.joshdreagan.artemis.plugins;

import org.apache.activemq.artemis.core.config.WildcardConfiguration;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public final class Helper {

  public static String wildcardToRegex(String wildcard, WildcardConfiguration wildcardConfiguration) {
    String delimiter = wildcardConfiguration.getDelimiterString();
    String singleWord = wildcardConfiguration.getSingleWordString();
    String anyWords = wildcardConfiguration.getAnyWordsString();

    String[] parts = wildcard.split(literal(delimiter));
    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      if (singleWord.equals(part)) {
        part = part.replace(singleWord, "[^" + literal(delimiter) + "]+");
      } else if (anyWords.equals(part)) {
        if (i != (parts.length - 1))
          throw new IllegalArgumentException(String.format("Any words placeholder [%s] must be the last part of the wildcard.", anyWords));

        part = part.replace(anyWords, ".*");
      } else {
        part = literal(part);
      }
      parts[i] = part;
    }
    return String.join(literal(delimiter), parts);
  }

  private static String literal(String string) {
    return "\\Q" + string + "\\E";
  }

  public static String toSimpleTypeString(String typeString) {
    Objects.requireNonNull(typeString, "Parameter 'typeString' cannot be null");

    return switch (typeString.toLowerCase()) {
      case "xml",
           "application/xml",
           "text/xml" -> "xml";
      case "json",
           "application/json" -> "json";
      case "yaml",
           "yml",
           "application/yaml",
           "application/yml",
           "application/x-yaml",
           "application/x-yml",
           "text/yaml",
           "text/yml",
           "text/x-yaml",
           "text/x-yml" -> "yaml";
      case "text",
           "txt",
           "text/plain" -> "text";
      default -> throw new IllegalArgumentException("Unknown type: " + typeString);
    };
  }

  public static String getConfigType(URL url) throws IOException {
    String type = getQueryParameter(url, "type", String.class);
    if (type != null) {
      return toSimpleTypeString(type);
    }

    String prefix = url.getProtocol();
    if (prefix.equals("file")) {
      String urlPath = url.getPath();
      String rfcType = Files.probeContentType(Paths.get(urlPath));
      if (rfcType != null) {
        return toSimpleTypeString(rfcType);
      }
    }

    String urlPath = url.getPath();
    if (urlPath != null) {
      if (urlPath.contains(".")) {
        String extension = urlPath.substring(urlPath.lastIndexOf(".") + 1);
        return toSimpleTypeString(extension);
      }
    }

    throw new IllegalArgumentException("Could not determine config type from URL: " + url);
  }

  public static Map<String, List<String>> getQueryParameters(URL url) {
    Objects.requireNonNull(url, "Parameter 'url' cannot be null");

    String queryString = url.getQuery();
    if (queryString == null) {
      return Collections.emptyMap();
    }

    Map<String, List<String>> queryParams = new HashMap<>();
    String[] queryStringParts = queryString.split("\\Q&\\E");
    for (String queryStringPart : queryStringParts) {
      String[] keyValuePair = queryStringPart.split("\\Q=\\E");
      String key = keyValuePair[0];
      key = key.replaceAll("\\[]&", "");
      if (keyValuePair.length > 1) {
        String value = keyValuePair[1];
        String[] valueParts = value.split(",");
        for (String valuePart : valueParts) {
          queryParams.computeIfAbsent(key, k -> new ArrayList<>()).add(valuePart);
        }
      }
    }
    return queryParams;
  }

  @SuppressWarnings("unchecked")
  public static <T> T getQueryParameter(URL url, String key, Class<T> valueType) {
    key = key.replaceAll("\\[]&", "");
    Map<String, List<String>> queryParams = getQueryParameters(url);
    List<String> values = queryParams.get(key);
    if (values == null || values.isEmpty()) {
      return null;
    }

    T result;
    if (valueType.isAssignableFrom(List.class)) {
      result = (T) new ArrayList<>(values);
    } else if (valueType.isAssignableFrom(String[].class)) {
      result = (T) values.toArray(new String[0]);
    } else if (valueType.isAssignableFrom(Set.class)) {
      result = (T) new HashSet<>(values);
    } else if (valueType.isAssignableFrom(String.class)) {
      result = (T) values.getFirst();
    } else {
      throw new IllegalArgumentException("Unsupported value type: " + valueType);
    }
    return result;
  }
}
