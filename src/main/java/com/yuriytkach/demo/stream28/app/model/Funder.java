package com.yuriytkach.demo.stream28.app.model;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import lombok.Data;

@Data
public class Funder {

  private final String name;
  private final LocalDateTime fundedAt;
  private final int amount;
  private final String currency;

  public Date getFundedDate() {
    return Date.from(fundedAt.toInstant(ZoneOffset.UTC));
  }

}
