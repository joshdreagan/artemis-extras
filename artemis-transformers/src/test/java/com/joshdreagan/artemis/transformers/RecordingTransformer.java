package com.joshdreagan.artemis.transformers;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.transformer.Transformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RecordingTransformer implements Transformer {

  static final List<String> applied = new ArrayList<>();

  private String id;

  @Override
  public void init(Map<String, String> properties) {
    id = properties.get("id");
  }

  @Override
  public Message transform(Message message) {
    applied.add(id);
    return message;
  }
}
