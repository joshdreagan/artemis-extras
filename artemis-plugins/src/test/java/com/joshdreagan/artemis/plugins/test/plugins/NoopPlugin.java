package com.joshdreagan.artemis.plugins.test.plugins;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;

import java.util.HashMap;
import java.util.Map;

public class NoopPlugin implements ActiveMQServerPlugin {

  public Map<String, String> properties;
  public boolean initialized = false;
  public boolean registered = false;
  public boolean unregistered = false;

  @Override
  public void init(Map<String, String> properties) {
    this.properties = new HashMap<>(properties);
    initialized = true;
  }

  @Override
  public void registered(ActiveMQServer server) {
    registered = true;
  }

  @Override
  public void unregistered(ActiveMQServer server) {
    unregistered = true;
  }
}
