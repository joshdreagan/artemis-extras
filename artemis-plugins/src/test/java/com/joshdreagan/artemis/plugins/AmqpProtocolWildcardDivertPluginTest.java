package com.joshdreagan.artemis.plugins;

class AmqpProtocolWildcardDivertPluginTest extends AbstractWildcardDivertPluginTest {

  String uri() {
    return "amqp://localhost:5672";
  }
}
