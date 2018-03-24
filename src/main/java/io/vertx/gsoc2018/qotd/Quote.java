package io.vertx.gsoc2018.qotd;

import io.vertx.core.json.JsonObject;

public class Quote {
  private Integer quoteId;
  private String text;
  private String author;

  public Quote(String text, String author) {
    this.quoteId = -1;
    this.text = text;
    this.author = author;
  }

  public Quote(int quoteId, String text, String author) {
    this.quoteId = quoteId;
    this.text = text;
    this.author = author;
  }

  public Quote(JsonObject json) {
    this.quoteId = json.getInteger("quote_id");
    this.text = json.getString("text");
    this.author = json.getString("author");
  }

  public Integer getQuoteId() {
    return quoteId;
  }

  public void setQuoteId(Integer quoteId) {
    this.quoteId = quoteId;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }
}
