package io.vertx.gsoc18.qotd;

public class Quote {
  private Integer quoteId;
  private String text;
  private String author;

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
