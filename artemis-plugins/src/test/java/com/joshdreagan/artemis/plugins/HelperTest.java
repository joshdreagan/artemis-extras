package com.joshdreagan.artemis.plugins;

import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  @Test
  void testGetConfigTypeUsesTypeQueryParameter() throws Exception {
    URL url = URI.create("file:/tmp/config.xml?type=YAML").toURL();

    assertEquals("yaml", Helper.getConfigType(url));
  }

  @Test
  void testGetConfigTypeUsesUrlExtensionWhenTypeIsNotSpecified() throws Exception {
    URL url = URI.create("http://localhost/config.YAML").toURL();

    assertEquals("yaml", Helper.getConfigType(url));
  }

  @Test
  void testGetConfigTypeThrowsWhenTypeCannotBeDetermined() throws Exception {
    URL url = URI.create("http://localhost/config").toURL();

    assertThrows(IllegalArgumentException.class, () -> Helper.getConfigType(url));
  }

  @Test
  void testGetQueryParametersReturnsAllValues() throws Exception {
    URL url = URI.create("http://localhost/config?name=first&name=second,third&enabled=true").toURL();

    Map<String, List<String>> queryParameters = Helper.getQueryParameters(url);

    assertEquals(Map.of(
      "name", List.of("first", "second", "third"),
      "enabled", List.of("true")
    ), queryParameters);
  }

  @Test
  void testGetQueryParametersReturnsEmptyMapWithoutQuery() throws Exception {
    URL url = URI.create("http://localhost/config").toURL();

    assertTrue(Helper.getQueryParameters(url).isEmpty());
  }

  @Test
  void testGetQueryParametersRejectsNullUrl() {
    assertThrows(NullPointerException.class, () -> Helper.getQueryParameters(null));
  }

  @Test
  void testGetQueryParameterReturnsString() throws Exception {
    URL url = URI.create("http://localhost/config?name=first&name=second").toURL();

    assertEquals("first", Helper.getQueryParameter(url, "name", String.class));
  }

  @Test
  void testGetQueryParameterReturnsListArrayAndSet() throws Exception {
    URL url = URI.create("http://localhost/config?name=first&name=second,first").toURL();

    assertEquals(List.of("first", "second", "first"),
      Helper.getQueryParameter(url, "name", List.class));
    assertArrayEquals(new String[]{"first", "second", "first"},
      Helper.getQueryParameter(url, "name", String[].class));
    assertEquals(Set.of("first", "second"),
      Helper.getQueryParameter(url, "name", Set.class));
  }

  @Test
  void testGetQueryParameterReturnsNullForMissingParameter() throws Exception {
    URL url = URI.create("http://localhost/config?name=value").toURL();

    assertNull(Helper.getQueryParameter(url, "missing", String.class));
  }

  @Test
  void testGetQueryParameterRejectsUnsupportedValueType() throws Exception {
    URL url = URI.create("http://localhost/config?name=value").toURL();

    assertThrows(IllegalArgumentException.class,
      () -> Helper.getQueryParameter(url, "name", Integer.class));
  }
}
