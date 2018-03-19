package io.vertx.gsoc18.qotd;

import io.reactivex.Single;
import io.vertx.reactivex.core.Vertx;

import java.util.List;

public class QuoteOfTheDayService {
  private QotdStore store;

  public QuoteOfTheDayService(Vertx vertx) {
    store = new QotdStore(vertx);
  }

  public Single<List<Quote>> getAllQuotes() {
    return store.getAllQuotes().toList();
  }

  public Single<Boolean> insertQuote(Quote quote) {
    return store.insertQuote(quote).map(res -> !res.isEmpty());
  }
}
