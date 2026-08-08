package com.joshdreagan.artemis.plugins;

import com.joshdreagan.artemis.transformers.DivertHeadersTransformer;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class AbstractWildcardDivertPluginTest {

  protected EmbeddedActiveMQ server;

  @BeforeEach
  protected void setUp() throws Exception {
    server = new EmbeddedActiveMQ();
    server.setConfigResourcePath("artemis/broker.xml");
    server.start();
  }

  @AfterEach
  protected void tearDown() throws Exception {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  abstract String uri();

  protected void testDivert(String uri, String address, String forwardingAddress, int addressCount, int forwardingCount, Duration minWaitTime, Duration maxWaitTime) throws Exception {
    testDivert(uri, address, forwardingAddress, addressCount, forwardingCount, minWaitTime, maxWaitTime, "hello world", null);
  }

  protected void testDivert(String uri, String address, String forwardingAddress, int addressCount, int forwardingCount, Duration minWaitTime, Duration maxWaitTime, String messageText, Map<String, String> messageProperties) throws Exception {
    ConnectionFactory connectionFactory;
    if (uri.startsWith("tcp://")) {
      connectionFactory = new ActiveMQJMSConnectionFactory(uri);
    } else if (uri.startsWith("amqp://")) {
      connectionFactory = new JmsConnectionFactory(uri);
    } else {
      throw new IllegalArgumentException("Unsupported URI: " + uri);
    }
    try (Connection connection = connectionFactory.createConnection();) {
      try (Session addressProducerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
           Session addressConsumerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
           Session forwardingConsumerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);) {
        Topic originalTopic = addressConsumerSession.createTopic(address);
        Queue forwardingQueue = forwardingConsumerSession.createQueue(forwardingAddress);
        try (MessageProducer addressProducer = addressProducerSession.createProducer(originalTopic);
             MessageConsumer addressConsumer = addressConsumerSession.createConsumer(originalTopic);
             MessageConsumer forwardingConsumer = forwardingConsumerSession.createConsumer(forwardingQueue);) {
          List<Message> addressMessages = new ArrayList<>();
          addressConsumer.setMessageListener(addressMessages::add);
          List<Message> forwardingMessages = new ArrayList<>();
          forwardingConsumer.setMessageListener(forwardingMessages::add);
          connection.start();

          TextMessage message = addressProducerSession.createTextMessage();
          message.setText(messageText);
          if (messageProperties != null) {
            messageProperties.forEach((k, v) -> assertDoesNotThrow(() -> {
              message.setStringProperty(k, v);
            }));
          }
          addressProducer.send(message);

          Awaitility.await().during(minWaitTime).atMost(maxWaitTime).until(() -> addressMessages.size() >= addressCount);
          Awaitility.await().during(minWaitTime).atMost(maxWaitTime).until(() -> forwardingMessages.size() >= forwardingCount);

          assertEquals(addressCount, addressMessages.size());
          assertEquals(forwardingCount, forwardingMessages.size());
          if (forwardingCount > 0) {
            assertEquals(address, forwardingMessages.getFirst().getStringProperty(DivertHeadersTransformer.PRE_DIVERT_ADDRESS));
          }
        }
      }
    }
  }

  @Test
  void testInternal() throws Exception {
    testDivert(uri(), "APP.US.EAST.NY.INTERNAL", "APP.FORWARD", 1, 0, Duration.ZERO, Duration.ofSeconds(1));
  }

  @Test
  void testInternalAndHub() throws Exception {
    testDivert(uri(), "APP.US.EAST.NY.INTERNAL_AND_HUB", "APP.FORWARD", 1, 1, Duration.ZERO, Duration.ofSeconds(1));
  }

  @Test
  void testSpokeToSpoke() throws Exception {
    testDivert(uri(), "APP.US.WEST.CA.SPOKE_TO_SPOKE", "APP.FORWARD", 0, 1, Duration.ZERO, Duration.ofSeconds(1));
  }

  @Test
  void testAll() throws Exception {
    testDivert(uri(), "APP.ALL", "APP.FORWARD", 1, 0, Duration.ZERO, Duration.ofSeconds(1));
  }

  @Test
  void testCountryAll() throws Exception {
    testDivert(uri(), "APP.US.ALL", "APP.FORWARD", 1, 0, Duration.ZERO, Duration.ofSeconds(1));
  }

  @Test
  void testCountryAndRegionAll() throws Exception {
    testDivert(uri(), "APP.US.EAST.ALL", "APP.FORWARD", 1, 0, Duration.ZERO, Duration.ofSeconds(1));
  }

  @Test
  void testHubToSpoke() throws Exception {
    testDivert(uri(), "APP.US.EAST.NY.HUB_TO_SPOKE", "APP.FORWARD", 1, 0, Duration.ZERO, Duration.ofSeconds(1));
  }

  @Test
  void testTransformer() throws Exception {
    testDivert(uri(), "TEST.TRANSFORMER", "TEST.TRANSFORMER.FORWARD", 0, 1, Duration.ZERO, Duration.ofSeconds(1));
  }

  @Test
  void testFilterMatches() throws Exception {
    testDivert(uri(), "TEST.FILTER", "TEST.FILTER.FORWARD", 0, 1, Duration.ofMillis(500), Duration.ofSeconds(1), "hello world", Map.of("test_header", "some_value"));
  }

  @Test
  void testFilterNotMatches() throws Exception {
    testDivert(uri(), "TEST.FILTER", "TEST.FILTER.FORWARD", 1, 0, Duration.ofMillis(500), Duration.ofSeconds(1), "hello world", Map.of("test_header", "some_other_value"));
  }
}
