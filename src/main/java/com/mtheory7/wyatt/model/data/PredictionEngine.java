package com.mtheory7.wyatt.model.data;

import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.mtheory7.wyatt.model.DataIdentifier;
import com.mtheory7.wyatt.utils.CalcUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PredictionEngine {
  public Double buyBackAfterThisPercentage;
  public Double sellPriceMultiplier;
  public Double targetPrice;
  private List<AverageData> averageData;
  private List<Double> targetPrices;
  private Map<CandlestickInterval, List<Candlestick>> candleMap;

  /** PredictionEngine constructor */
  public PredictionEngine(Double buyBackAfterThisPercentage, Double sellPriceMultiplier) {
    this.buyBackAfterThisPercentage = buyBackAfterThisPercentage;
    this.sellPriceMultiplier = sellPriceMultiplier;
	averageData = new ArrayList<>();
    targetPrices = new ArrayList<>();
    candleMap = new HashMap<>();
  }

  /**
   * Use mind data to find averages and then predict a target price to sell at.
   *
   * @param mindData The data to use for target calculation
   */
  public void executeThoughtProcess(MindData mindData, String ticker) {
    for (HashMap.Entry<DataIdentifier, List<Candlestick>> entry :
        mindData.getCandlestickData().entrySet()) {
      if (entry.getKey().getInterval() == CandlestickInterval.ONE_MINUTE
          && entry.getKey().getTicker().equals(ticker)) {
        candleMap.put(CandlestickInterval.ONE_MINUTE, entry.getValue());
      }
      else if (entry.getKey().getInterval() == CandlestickInterval.THREE_MINUTES
          && entry.getKey().getTicker().equals(ticker)) {
        candleMap.put(CandlestickInterval.THREE_MINUTES, entry.getValue());
      }
      else if (entry.getKey().getInterval() == CandlestickInterval.FIVE_MINUTES
          && entry.getKey().getTicker().equals(ticker)) {
        candleMap.put(CandlestickInterval.FIVE_MINUTES, entry.getValue());
      }
      else if (entry.getKey().getInterval() == CandlestickInterval.FIFTEEN_MINUTES
          && entry.getKey().getTicker().equals(ticker)) {
        candleMap.put(CandlestickInterval.FIFTEEN_MINUTES, entry.getValue());
      }
    }
    for (HashMap.Entry<CandlestickInterval, List<Candlestick>> entry : candleMap.entrySet()) {
      averageData.add(calculateAverageData(entry));
    }
    for (AverageData avg : averageData) {
      Double target =
          Math.max(
              Math.max(Math.max(avg.getLowAvg(), avg.getOpenAvg()), avg.getHighAvg()),
              avg.getCloseAvg());
      targetPrices.add(target);
    }
    // Calculate target price by maxing the targetPrices and add a small percentage
    targetPrice = CalcUtils.floorTo(maxTarget(targetPrices) * sellPriceMultiplier);
  }

  /**
   * Use a list of candlesticks to calculate some AverageData values to use for target price
   * generation
   *
   * @param entry The list of Candlesticks
   * @return The AverageData object
   */
  private AverageData calculateAverageData(
      HashMap.Entry<CandlestickInterval, List<Candlestick>> entry) {
    AverageData averageData = new AverageData();
    Double low = 0.0;
    Double open = 0.0;
    Double close = 0.0;
    Double high = 0.0;
    for (Candlestick candle : entry.getValue()) {
      low += Double.valueOf(candle.getLow());
      open += Double.valueOf(candle.getOpen());
      close += Double.valueOf(candle.getClose());
      high += Double.valueOf(candle.getHigh());
    }
    averageData.setLowAvg(low / entry.getValue().size());
    averageData.setOpenAvg(open / entry.getValue().size());
    averageData.setCloseAvg(close / entry.getValue().size());
    averageData.setHighAvg(high / entry.getValue().size());
    return averageData;
  }

  /**
   * Average the list of target prices
   *
   * @param list The list to average
   * @return The list average of the list
   */
  private Double maxTarget(List<Double> list) {
    Double highest = 0.0;
    for (Double num : list) {
      if (num > highest) {
        highest = num;
      }
    }
    return highest;
  }
}
