package com.joshdreagan.artemis.plugins;

import com.joshdreagan.artemis.plugins.test.plugins.NoopPlugin;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExternallyConfiguredPluginTest {

  protected EmbeddedActiveMQ server;

  @BeforeEach
  protected void setUp() throws Exception {
    server = new EmbeddedActiveMQ();
    server.setConfigResourcePath("artemis/broker-external-plugins.xml");
    server.start();
  }

  @AfterEach
  protected void tearDown() throws Exception {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  protected void testExternalConfigFile(String location) throws Exception {
    ExternallyConfiguredPlugin externallyConfiguredPlugin = new ExternallyConfiguredPlugin();
    externallyConfiguredPlugin.init(Map.of(ExternallyConfiguredPlugin.LOCATION, location));
    server.getActiveMQServer().registerBrokerPlugin(externallyConfiguredPlugin);

    List<ActiveMQServerBasePlugin> plugins = server.getActiveMQServer().getBrokerPlugins();
    assertEquals(3, plugins.size());

    ActiveMQServerBasePlugin plugin1 = plugins.get(1);
    assertInstanceOf(NoopPlugin.class, plugin1);
    NoopPlugin noopPlugin1 = (NoopPlugin) plugin1;
    assertTrue(noopPlugin1.initialized);
    assertTrue(noopPlugin1.registered);
    assertEquals("value1", noopPlugin1.properties.get("key1"));
    assertEquals("value2", noopPlugin1.properties.get("key2"));

    ActiveMQServerBasePlugin plugin2 = plugins.get(2);
    assertInstanceOf(NoopPlugin.class, plugin2);
    NoopPlugin noopPlugin2 = (NoopPlugin) plugin2;
    assertTrue(noopPlugin2.initialized);
    assertTrue(noopPlugin2.registered);
    assertEquals("value3", noopPlugin2.properties.get("key3"));
    assertEquals("value4", noopPlugin2.properties.get("key4"));
  }

  @Test
  protected void testExternalJsonConfigFile() throws Exception {
    testExternalConfigFile("target/test-classes/artemis/plugins.json");
  }

  @Test
  protected void testExternalYamlConfigFile() throws Exception {
    testExternalConfigFile("target/test-classes/artemis/plugins.yaml");
  }

  @Test
  protected void testExternalXmlConfigFile() throws Exception {
    testExternalConfigFile("target/test-classes/artemis/plugins.xml");
  }
}
