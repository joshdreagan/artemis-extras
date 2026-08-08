package com.joshdreagan.artemis.transformers;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.transformer.Transformer;

public class DivertHeadersTransformer implements Transformer {

  public static final String PRE_DIVERT_ADDRESS = "PreDivertAddress";
  public static final String PRE_DIVERT_QUEUE = "PreDivertQueue";
  public static final String PRE_DIVERT_ROUTING_TYPE = "PreDivertRoutingType";
  public static final String PRE_DIVERT_MESSAGE_ID = "PreDivertMessageId";

  @Override
  public Message transform(Message message) {
    Message result = message;
    if (!result.containsProperty(PRE_DIVERT_ADDRESS)) {
      String originalAddress = result.getAnnotationString(Message.HDR_ORIGINAL_ADDRESS);
      if (originalAddress != null && !originalAddress.isEmpty()) {
        result = result.putStringProperty(PRE_DIVERT_ADDRESS, originalAddress);
      }
    }
    if (!result.containsProperty(PRE_DIVERT_QUEUE)) {
      String originalQueue = result.getAnnotationString(Message.HDR_ORIGINAL_QUEUE);
      if (originalQueue != null && !originalQueue.isEmpty()) {
        result = result.putStringProperty(PRE_DIVERT_QUEUE, originalQueue);
      }
    }
    if (!result.containsProperty(PRE_DIVERT_ROUTING_TYPE)) {
      String originalRoutingType = result.getAnnotationString(Message.HDR_ORIG_ROUTING_TYPE);
      if (originalRoutingType != null && !originalRoutingType.isEmpty()) {
        result = result.putStringProperty(PRE_DIVERT_ROUTING_TYPE, originalRoutingType);
      }
    }
    if (!result.containsProperty(PRE_DIVERT_MESSAGE_ID)) {
      String originalMessageId = result.getAnnotationString(Message.HDR_ORIG_MESSAGE_ID);
      if (originalMessageId != null && !originalMessageId.isEmpty()) {
        result = result.putStringProperty(PRE_DIVERT_MESSAGE_ID, originalMessageId);
      }
    }
    result.reencode();
    return result;
  }
}
