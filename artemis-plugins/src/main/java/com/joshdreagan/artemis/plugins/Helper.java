package com.joshdreagan.artemis.plugins;

import org.apache.activemq.artemis.core.config.WildcardConfiguration;

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
}
