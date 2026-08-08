package com.joshdreagan.artemis.plugins.test.transformers;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.transformer.Transformer;

import java.util.Map;

public class HeaderTransformer implements Transformer {

  private String headerName;
  private String headerValue;

  @Override
  public void init(Map<String, String> properties) {
    headerName = properties.get("header-name");
    headerValue = properties.get("header-value");
  }

  @Override
  public Message transform(Message message) {
    Message result = message;
    result = result.putStringProperty(headerName, headerValue);
    result.reencode();
    return result;
  }
}
