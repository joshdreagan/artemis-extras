package com.joshdreagan.artemis.plugins;

import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class HelperTest {

  private WildcardConfiguration wildcardConfiguration;

  @BeforeEach
  void setUp() {
    wildcardConfiguration = new WildcardConfiguration()
      .setDelimiter('.')
      .setSingleWord('*')
      .setAnyWords('#');
  }

  @Test
  void testInputWithNoDelimiters() {
    String address = "orders";
    String wildcard = "orders";
    String regex = Helper.wildcardToRegex(wildcard, wildcardConfiguration);
    assertTrue(Pattern.matches(regex, address));
  }

  @Test
  void testSingleWordWildcardAtEnd() {
    String wildcard = "orders.*";
    String regex = Helper.wildcardToRegex(wildcard, wildcardConfiguration);
    assertTrue(Pattern.matches(regex, "orders.ne"));
    assertFalse(Pattern.matches(regex, "orders.ne.ny"));
  }

  @Test
  void testSingleWordWildcardInMiddle() {
    String wildcard = "orders.*.ny";
    String regex = Helper.wildcardToRegex(wildcard, wildcardConfiguration);
    assertTrue(Pattern.matches(regex, "orders.ne.ny"));
    assertTrue(Pattern.matches(regex, "orders.se.ny"));
    assertFalse(Pattern.matches(regex, "orders.ne.nj"));
    assertFalse(Pattern.matches(regex, "orders.ne"));
  }

  @Test
  void testAnyWordsWildcardAtEnd() {
    String wildcard = "orders.#";
    String regex = Helper.wildcardToRegex(wildcard, wildcardConfiguration);
    assertTrue(Pattern.matches(regex, "orders.ne"));
    assertTrue(Pattern.matches(regex, "orders.se"));
    assertTrue(Pattern.matches(regex, "orders.ne.ny"));
    assertTrue(Pattern.matches(regex, "orders.se.ga"));
    assertFalse(Pattern.matches(regex, "products.cogs"));
  }

  @Test
  void testAnyWordsWildcardInMiddle() {
    String wildcard = "orders.#.ny";
    assertThrows(IllegalArgumentException.class,
      () -> Helper.wildcardToRegex(wildcard, wildcardConfiguration));
  }
}
