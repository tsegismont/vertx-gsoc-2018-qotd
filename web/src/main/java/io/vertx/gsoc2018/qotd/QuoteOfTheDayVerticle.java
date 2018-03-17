package io.vertx.gsoc2018.qotd;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.gsoc18.qotd.QuoteOfTheDayService;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;

public class QuoteOfTheDayVerticle extends AbstractVerticle {

  private final Logger LOGGER = LoggerFactory.getLogger(QuoteOfTheDayVerticle.class.getName());
  private QuoteOfTheDayService quoteOfTheDayService;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    int port = config().getInteger("http.port", 8080);
    quoteOfTheDayService = new QuoteOfTheDayService(vertx);

    Router router = Router.router(vertx);

    vertx.createHttpServer().requestHandler(router::accept).rxListen(port)
      .subscribe(res -> {
        LOGGER.info("Initialized http server successfully");
      }, err -> LOGGER.error("Failed to initialize http server: " + err.getMessage()));
  }
}
