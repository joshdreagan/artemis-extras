package com.joshdreagan.artemis.transformers;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.transformer.Transformer;

public class NoopTransformer implements Transformer {

  @Override
  public Message transform(Message message) {
    return message;
  }
}
