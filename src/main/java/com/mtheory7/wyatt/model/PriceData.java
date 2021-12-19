package com.mtheory7.wyatt.model;

import com.google.gson.Gson;

public class PriceData {
  private Double ticker;
  private Double eth;
  private Double doge;
  private Double gold;
  private Double silver;
  private Double platinum;

  public Double getTicker() {
    return ticker;
  }

  public void setTicker(Double ticker) {
    this.ticker = ticker;
  }

  public Double getEth() {
    return eth;
  }

  public void setEth(Double eth) {
    this.eth = eth;
  }

  public Double getDoge() {
    return doge;
  }

  public void setDoge(Double doge) {
    this.doge = doge;
  }

  public Double getGold() {
    return gold;
  }

  public void setGold(Double gold) {
    this.gold = gold;
  }

  public Double getSilver() {
    return silver;
  }

  public void setSilver(Double silver) {
    this.silver = silver;
  }

  public Double getPlatinum() {
    return platinum;
  }

  public void setPlatinum(Double platinum) {
    this.platinum = platinum;
  }

  @Override
  public String toString() {
    return new Gson().toJson(this);
  }
}
