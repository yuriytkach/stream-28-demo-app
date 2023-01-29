package com.yuriytkach.demo.stream28.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties("api")
public class ApiProperties {
  private final String url;
  private final TimeoutProperties timeouts;

  @Data
  public static class TimeoutProperties {
    private final int connection;
    private final int read;
  }
}
