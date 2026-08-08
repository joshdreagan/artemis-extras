package com.joshdreagan.artemis.transformers;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DivertHeadersTransformerTest {

  @Test
  void testCopiesOriginalDivertAnnotationsToProperties() {
    Transformer divertHeadersTransformer = new DivertHeadersTransformer();

    String originalMessageId = "message-1";
    String originalAddress = "orders";
    String originalQueue = "orders.queue";
    String originalRoutingType = "ANYCAST";

    Message message = new CoreMessage();
    message
      .setAnnotation(Message.HDR_ORIG_MESSAGE_ID, originalMessageId)
      .setAnnotation(Message.HDR_ORIGINAL_ADDRESS, originalAddress)
      .setAnnotation(Message.HDR_ORIGINAL_QUEUE, originalQueue)
      .setAnnotation(Message.HDR_ORIG_ROUTING_TYPE, originalRoutingType);

    message = divertHeadersTransformer.transform(message);

    assertEquals(originalMessageId, message.getStringProperty(DivertHeadersTransformer.PRE_DIVERT_MESSAGE_ID));
    assertEquals(originalAddress, message.getStringProperty(DivertHeadersTransformer.PRE_DIVERT_ADDRESS));
    assertEquals(originalQueue, message.getStringProperty(DivertHeadersTransformer.PRE_DIVERT_QUEUE));
    assertEquals(originalRoutingType, message.getStringProperty(DivertHeadersTransformer.PRE_DIVERT_ROUTING_TYPE));
  }

  @Test
  void testDoesNotOverwriteExistingDivertProperties() {
    Transformer divertHeadersTransformer = new DivertHeadersTransformer();

    String originalMessageId = "message-1";
    String originalAddress = "orders";
    String originalQueue = "orders.queue";
    String originalRoutingType = "ANYCAST";

    Message message = new CoreMessage();
    message
      .setAnnotation(Message.HDR_ORIG_MESSAGE_ID, originalMessageId)
      .setAnnotation(Message.HDR_ORIGINAL_ADDRESS, originalAddress)
      .setAnnotation(Message.HDR_ORIGINAL_QUEUE, originalQueue)
      .setAnnotation(Message.HDR_ORIG_ROUTING_TYPE, originalRoutingType);

    message = divertHeadersTransformer.transform(message);

    assertEquals(originalMessageId, message.getStringProperty(DivertHeadersTransformer.PRE_DIVERT_MESSAGE_ID));
    assertEquals(originalAddress, message.getStringProperty(DivertHeadersTransformer.PRE_DIVERT_ADDRESS));
    assertEquals(originalQueue, message.getStringProperty(DivertHeadersTransformer.PRE_DIVERT_QUEUE));
    assertEquals(originalMessageId, message.getStringProperty(DivertHeadersTransformer.PRE_DIVERT_MESSAGE_ID));

    String forwardedPostfix = "_forwarded";

    message
      .setAnnotation(Message.HDR_ORIG_MESSAGE_ID, originalMessageId + forwardedPostfix)
      .setAnnotation(Message.HDR_ORIGINAL_ADDRESS, originalAddress + forwardedPostfix)
      .setAnnotation(Message.HDR_ORIGINAL_QUEUE, originalQueue + forwardedPostfix)
      .setAnnotation(Message.HDR_ORIG_ROUTING_TYPE, originalRoutingType + forwardedPostfix);

    message = divertHeadersTransformer.transform(message);

    assertEquals(originalMessageId, message.getStringProperty(DivertHeadersTransformer.PRE_DIVERT_MESSAGE_ID));
    assertEquals(originalAddress, message.getStringProperty(DivertHeadersTransformer.PRE_DIVERT_ADDRESS));
    assertEquals(originalQueue, message.getStringProperty(DivertHeadersTransformer.PRE_DIVERT_QUEUE));
    assertEquals(originalMessageId, message.getStringProperty(DivertHeadersTransformer.PRE_DIVERT_MESSAGE_ID));
  }
}
