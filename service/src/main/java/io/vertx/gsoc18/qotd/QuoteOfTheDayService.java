package io.vertx.gsoc18.qotd;

import io.vertx.reactivex.core.Vertx;

public class QuoteOfTheDayService {
  private QotdStore store;

  public QuoteOfTheDayService(Vertx vertx) {
    store = new QotdStore(vertx);
  }
}
