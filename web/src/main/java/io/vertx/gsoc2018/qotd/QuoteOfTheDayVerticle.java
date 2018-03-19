package io.vertx.gsoc2018.qotd;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;

import io.vertx.gsoc18.qotd.Quote;
import io.vertx.gsoc18.qotd.QuoteOfTheDayService;

public class QuoteOfTheDayVerticle extends AbstractVerticle {

  private final Logger LOGGER = LoggerFactory.getLogger(QuoteOfTheDayVerticle.class.getName());
  private QuoteOfTheDayService quoteOfTheDayService;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    int port = config().getInteger("http.port", 8080);
    quoteOfTheDayService = new QuoteOfTheDayService(vertx);

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.get("/quotes").handler(this::getQuotes);
    router.post("/quotes").handler(this::addQuote);

    vertx.createHttpServer().requestHandler(router::accept).rxListen(port)
      .subscribe(res -> {
        LOGGER.info("Initialized http server successfully");
      }, err -> LOGGER.error("Failed to initialize http server: " + err.getMessage()));
  }

  public void getQuotes(RoutingContext routingContext) {
    quoteOfTheDayService.getAllQuotes().subscribe(quotes -> routingContext.response()
      .putHeader("content-type", "application/json; charset=utf-8")
      .end(Json.encodePrettily(quotes)), error -> routingContext.response().end("Error occurred"));
  }

  public void addQuote(RoutingContext routingContext) {
    String text = routingContext.request().getParam("text");
    String author = routingContext.request().getParam("author");
    if(text == null) {
      routingContext.response().setStatusCode(404).end();
      return;
    }
    if(author == null || author.equals("")) author = "Unknown";
    Quote quote = new Quote(text, author);
    quoteOfTheDayService.insertQuote(quote).subscribe(res -> {
      if(res) {
        routingContext.response().end("Successfully added quote");
      } else {
        routingContext.response().end("Some error occurred");
      }
    }, error -> routingContext.response().end("Some error occurred"));
  }
}
