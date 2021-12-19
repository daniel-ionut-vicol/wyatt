package com.mtheory7.wyatt.mind;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import com.mtheory7.wyatt.model.DataIdentifier;
import com.mtheory7.wyatt.model.data.MindData;
import com.mtheory7.wyatt.model.data.PredictionEngine;
import com.mtheory7.wyatt.utils.CalcUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static com.binance.api.client.domain.account.NewOrder.*;

@Service
public class Wyatt {

  private static final Logger logger = Logger.getLogger(Wyatt.class);
  private static final CandlestickInterval[] intervalList = {
    CandlestickInterval.ONE_MINUTE, CandlestickInterval.THREE_MINUTES,
    CandlestickInterval.FIVE_MINUTES, CandlestickInterval.FIFTEEN_MINUTES
  };

  public String ticker;
  public String from;
  public String to;
  public static boolean DEVELOPMENT_MODE;
  public boolean currentState = true;
  private boolean EXECUTE_TWEETS = false;
  private Double lastTargetPrice = 1000000.0;
  private Double buyBackPrice = 0.0;
  private Double openBuyBackPrice = 0.0;
  private Double openBuyBackAmt = 0.0;
  private MindData mindData;
  private PredictionEngine predictionEngine;
  private BinanceApiRestClient client;
  private String consumerKey;
  private String consumerSecret;
  private String accessToken;
  private String accessTokenSecret;

  @Value("${startingUSD}")
  private Double INITIAL_INVESTMENT_USD;

  @Value("${versionValue}")
  private String VERSION;

  @Value("${developmentMode}")
  public void setDevelopmentMode(boolean mode) {
    DEVELOPMENT_MODE = mode;
  }

	public Wyatt() {
	}

	public void setTicker(String to, String from) {
		this.to= to;
		this.from= from;
		this.ticker = to+from;
	}
  
  /** Resets Wyatt's memory. This is necessary to do or memory leaks will be possible */
  public void reset() {
    this.mindData = new MindData();
    this.predictionEngine = new PredictionEngine();
  }

  /**
   * Returns the current state of Wyatt. This relates to whether the bot is waiting for a buy back
   * or not
   *
   * @return String for the UI stating Wyatt's status
   */
  public String getCurrentStateString() {
    if (currentState) {
      return "Waiting to sell";
    } else {
      return "Waiting for buy back";
    }
  }

  /**
   * Returns the string of the current running version
   *
   * @return String containing version number
   */
  public String getVersion() {
    return VERSION;
  }

  /**
   * Returns the current price according to Binance
   *
   * @return Current price in Double form
   */
  public Double getCurrentPrice() {
    TickerStatistics tickerStatistics = client.get24HrPriceStatistics(ticker);
    return Double.valueOf(tickerStatistics.getLastPrice());
  }

  public Double getCurrentPrice(String ticker) {
    TickerStatistics tickerStatistics = client.get24HrPriceStatistics(ticker);
    return Double.valueOf(tickerStatistics.getLastPrice());
  }

  public Double getInitialInvestmentUSD() {
    return INITIAL_INVESTMENT_USD;
  }

  /**
   * Returns the latest known target price
   *
   * @return Latest target price Double
   */
  public Double getCurrentTargetPrice() {
    return lastTargetPrice;
  }

  /**
   * Returns the latest known buy back price
   *
   * @return Latest buy back price
   */
  public Double getCurrentBuyBackPrice() {
    return buyBackPrice;
  }

  /**
   * Returns the open buy back price in USD
   *
   * @return Open buy back price
   */
  public Double getOpenBuyBackPrice() {
    return openBuyBackPrice;
  }

  /**
   * Returns the open buy back amount in to
   *
   * @return Open buy back amount
   */
  public Double getOpenBuyBackAmt() {
    return openBuyBackAmt;
  }

  /**
   * Returns if the bot currently has Twitter credentials set
   *
   * @return
   */
  public boolean isEXECUTE_TWEETS() {
    return EXECUTE_TWEETS;
  }

  /**
   * Returns the order history to the UI for displaying in the /orders endpoint
   *
   * @return String (HTML) of the order history
   */
  public String getOrderHistory() {
    String response = "";
    List<Trade> trades = client.getMyTrades(ticker);
    for (Trade trade : trades) {
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/dd/yyyy' 'HH:mm:ss:S");
      response =
          new StringBuilder()
              .append("<br><font color=\"")
              .append(trade.isBuyer() ? "green" : "red")
              .append("\">")
              .append(trade.getOrderId())
              .append(": Date/Time: ")
              .append(simpleDateFormat.format(trade.getTime()))
              .append(": ")
              .append(trade.getQty())
              .append(" "+ticker+" @ $")
              .append(String.format("%.2f", Double.valueOf(trade.getPrice())))
              .append("</font>")
              .append(response)
              .toString();
    }
    return response;
  }

  /**
   * Returns the current balances to the UI for displaying in the /status endpoint
   *
   * @return String (HTML) of the current balance
   */
  public String getBalances() {
    StringBuilder response = new StringBuilder();
    Account account = client.getAccount();
    List<AssetBalance> balances = account.getBalances();
    for (AssetBalance balance : balances) {
      Double amount = Double.valueOf(balance.getFree()) + Double.valueOf(balance.getLocked());
      if (amount > 0.0) {
    	  if (getAssetName(balance.getAsset()).equals(to) || getAssetName(balance.getAsset()).equals(from)) {
    	  response
            .append("<br>&nbsp;&nbsp;-&nbsp;")
            .append(amount)
            .append(" ")
            .append(balance.getAsset());
    	  }
      }
    }
    return response.toString();
  }

  /**
   * Returns the open buy back percentage
   *
   * @return Open buy back percentage
   */
  public Double getOpenBuyBackPercentage() {
    return CalcUtils.roundTo((getCurrentPrice() / openBuyBackPrice - 1) * 100);
  }

  /**
   * Returns the current sell confidence
   *
   * @return Current sell confidence (%)
   */
  public Double getCurrentSellConfidence() {
    return CalcUtils.roundTo((getCurrentPrice() / getCurrentTargetPrice() * 100));
  }

  /**
   * Sets the credentials that are needed for interacting with Binance
   *
   * @param binanceAPIKey Binance API Key
   * @param binanceAPISecret Binance API Secret
   */
  public void setBinanceCreds(String binanceAPIKey, String binanceAPISecret) {
    logger.trace("Setting Binance credentials");
    mindData = new MindData();
    predictionEngine = new PredictionEngine();
    BinanceApiClientFactory factory =
        BinanceApiClientFactory.newInstance(binanceAPIKey, binanceAPISecret);
    client = factory.newRestClient();
  }

  /**
   * Sets the credentials that are needed for tweeting alerts when Wyatt decides to sell and buy
   * back.
   *
   * @param consumerKey Twitter Consumer Key
   * @param consumerSecret Twitter Consumer Secret
   * @param accessToken Twitter Access Token
   * @param accessTokenSecret Twitter Access Token Secret
   */
  public void setTwitterCreds(
      String consumerKey, String consumerSecret, String accessToken, String accessTokenSecret) {
    logger.trace("Setting Twitter credentials");
    this.consumerKey = consumerKey;
    this.consumerSecret = consumerSecret;
    this.accessToken = accessToken;
    this.accessTokenSecret = accessTokenSecret;
    EXECUTE_TWEETS = true;
    logger.trace("EXECUTE_TWEETS: " + EXECUTE_TWEETS);
  }

  /**
   * Returns the total balance of the account in current estimated usd
   *
   * @return Balance in usd
   */
  public String getCurrentBalance() {
    Account account = client.getAccount();
    // Pull the latest account balance info from Binance
    List<AssetBalance> balances = account.getBalances();
    Double estimatedBalance = 0.0;
		for (AssetBalance balance : balances) {
			//only calculate for what we are trading
			if (getAssetName(balance.getAsset()).equals(to) || getAssetName(balance.getAsset()).equals(from)) {
				Double amount = Double.valueOf(balance.getFree()) + Double.valueOf(balance.getLocked());
				if (amount > 0.0) {
					String asset = getAssetName(balance.getAsset());
					estimatedBalance += valueInUSD(amount, asset);
				}
			}
		}
    estimatedBalance = CalcUtils.roundTo(estimatedBalance);
    return estimatedBalance.toString();
  }

  /**
   * Return the current profit from the starting investment
   *
   * @return The current profit
   */
  public String getCurrentProfit() {
    Double percentOnInvenstment =
        CalcUtils.roundTo(
            (((Double.valueOf(getCurrentBalance()) / getInitialInvestmentUSD()) * 100) - 100));
    return percentOnInvenstment.toString();
  }

  /**
   * Estimate the value of a given amount/ticker in USD
   *
   * @param amount The amount of an asset
   * @param ticker The ticker of the asset to estimate
   */
  private Double valueInUSD(Double amount, String ticker) {
	if (ticker.equals("USDT")) {
      return amount ;
    } else {
    	double value = 0;
    	try {
    		value = Double.valueOf(client.get24HrPriceStatistics(ticker + "USDT").getLastPrice()) * amount;
    	}catch (BinanceApiException e) {
        	e.printStackTrace();
		}
      return value;
    }
  }

	private static String getAssetName(String assetName) {
		if (assetName.startsWith("LD")) {
			return assetName.substring(2);
		}
		return assetName;
	}
  
  /**
   * Retrieves data from the ticker data pulled from Binance. This data is then used later for
   * predicting a selling price.
   */
  public void gatherMindData() {
      for (CandlestickInterval interval : intervalList) {
        gatherIntervalData(mindData, interval,ticker);
        new CalcUtils().sleeper(500);
      }
  }

  /**
   * Use the gathered data to attempt to predict a price to sell at, and then a price to buy back
   * at. When the price exceeds that target value, perform a sell and a buy back to make an
   * incremental amount of money.
   */
  public void predictAndTrade() {
    if (DEVELOPMENT_MODE) {
      reportDevMode();
    }
    // Gather data calculate, and update Wyatt's target price and buy back
    predictionEngine.executeThoughtProcess(mindData, ticker);
    lastTargetPrice = predictionEngine.targetPrice;
    // Find current price and decide to sell
    Double currentPrice = CalcUtils.roundTo(getCurrentPrice());
    buyBackPrice = CalcUtils.roundTo(currentPrice * PredictionEngine.buyBackAfterThisPercentage);
    if (currentPrice > lastTargetPrice) {
      lastTargetPrice = currentPrice;
      predictionEngine.targetPrice = currentPrice;
    }
    double sellConfidence =
        CalcUtils.roundTo((currentPrice / predictionEngine.targetPrice * 100));
    logger.trace(
        "Current: $"
            + currentPrice
            + " Target: $"
            + predictionEngine.targetPrice
            + " Buy back: $"
            + buyBackPrice
            + " Sell confidence: "
            + sellConfidence
            + "%");
    List<Order> openOrders = client.getOpenOrders(new OrderRequest(ticker));
    if (!openOrders.isEmpty()) {
      currentState = false;
      logger.trace("Number of open "+ticker+" orders: " + openOrders.size());
      Order openOrder = openOrders.get(0);
      if (openOrder != null) {
        openBuyBackAmt = Double.valueOf(openOrder.getOrigQty());
        openBuyBackPrice = Double.valueOf(openOrder.getPrice());
        Double currentMargin = currentPrice / Double.valueOf(openOrder.getPrice());
        Double currentMarginPercent = CalcUtils.roundTo((currentMargin - 1) * 100);
        Double buyBackDifference =
            CalcUtils.roundTo((currentPrice - Double.valueOf(openOrder.getPrice())));
        logger.trace(
            "Current buy back: " + currentMarginPercent + "% ($" + buyBackDifference + ")");
        if (currentMarginPercent > 10
            || (System.currentTimeMillis() - openOrder.getTime()) > 432000000) {
          logger.trace("Deciding to submit a market buy back at $" + currentPrice);
          if (!DEVELOPMENT_MODE) {
            executeMarketBuyBack();
          } else {
            reportDevMode();
          }
        } else {
          logger.trace("Orders for "+ticker+" are not empty, not trading for 120 seconds...");
          new CalcUtils().sleeper(120000);
        }
      }
    } else {
      currentState = true;
    }
    if (((currentPrice >= lastTargetPrice) || ((currentPrice / lastTargetPrice) < 0.80)) && currentState) {
      // Find out how much free asset there is to trade
      Account account = client.getAccount();
      Double freeToFloored =
          CalcUtils.floorTo(Double.valueOf(account.getAssetBalance(to).getFree()));
      String message =
          "Selling "
              + freeToFloored
              + " "+to+" at $"
              + String.format("%.2f", currentPrice)
              + " and buying back at $"
              + String.format("%.2f", buyBackPrice);
      logger.info(message);
      if (!DEVELOPMENT_MODE) {
        performSellAndBuyBack(currentPrice, buyBackPrice, message);
      } else {
        reportDevMode();
      }
    }
  }

  /**
   * Function to connect to Binance, and pull the information in candle form used to run the bot.
   *
   * @param mindData The structure to save the data to
   * @param interval The interval to grab candle data for
   * @param ticker The ticker to grab candle data for
   */
  private void gatherIntervalData(MindData mindData, CandlestickInterval interval, String ticker) {
    List<Candlestick> candlesticks = new ArrayList<>();
    try {
      // Make the GET call to Binance
      candlesticks = client.getCandlestickBars(ticker, interval);
    } catch (Exception e) {
      logger.info("There was an exception while pulling interval data!");
      logger.trace("Interval: " + interval + " Ticker: " + ticker);
      logger.error("Error: ", e);
      logger.trace("Waiting for 120 seconds ...");
      new CalcUtils().sleeper(120000);
    }
    // Save the pulled data to the passed in data structure
    mindData.candlestickData.put(new DataIdentifier(interval, ticker), candlesticks);
    TickerStatistics ts = client.get24HrPriceStatistics(ticker);
    mindData.lastPriceData.put(
        new DataIdentifier(interval, ticker), ts);
    mindData.candlestickIntAvgData.put(
        new DataIdentifier(interval, ticker), new CalcUtils().findAveragePrice(candlesticks));
  }

  /**
   * Perform a sell and buy at the passed in values. Uses the Binance configuration to execute these
   * trades.
   *
   * @param sellPrice Price to sell at
   * @param buyPrice Price to buy at
   */
  private void performSellAndBuyBack(Double sellPrice, Double buyPrice, String message) {
    sendTweet(message);
    Account account = client.getAccount();
    // Find out how much free asset there is to trade
    Double freeToFloored =
        CalcUtils.floorTo(Double.valueOf(account.getAssetBalance(to).getFree()));
    logger.info("Amount of "+to+" to trade: " + freeToFloored);
    try {
      logger.info("Executing sell of: " + freeToFloored + " "+to+" @ $" + sellPrice);
      // Submit the binance sell
      NewOrderResponse performSell =
          client.newOrder(
              limitSell(
                  ticker,
                  TimeInForce.GTC,
                  freeToFloored.toString(),
                  sellPrice.toString()));
      logger.info("Trade submitted: " + performSell.getTransactTime());
      logger.trace("Switching currentState to false - Awaiting buy back");
      currentState = false;
    } catch (Exception e) {
      logger.error("There was an exception thrown during the sell?: " + e.getMessage());
    }
    new CalcUtils().sleeper(3000);
    // Wait and make sure that the trade executed. If not, keep waiting
    List<Order> openOrders = client.getOpenOrders(new OrderRequest(ticker));
    logger.trace("Number of open "+ticker+" orders: " + openOrders.size());
    while (!openOrders.isEmpty()) {
      logger.trace("Orders for "+ticker+" are not empty, waiting 3 seconds...");
      new CalcUtils().sleeper(3000);
      openOrders = client.getOpenOrders(new OrderRequest(ticker));
    }
    new CalcUtils().sleeper(3000);
    // Verify that we have the correct amount of asset to trade
    account = client.getAccount();
    Double freeUSDT = Double.valueOf(account.getAssetBalance("USDT").getFree());
    // Loop until above 10.0 USDT
    while (freeUSDT < 10.0) {
      logger.trace("Looping because we currently have less than 10 USDT. Waiting 15 seconds...");
      new CalcUtils().sleeper(15000);
      account = client.getAccount();
      freeUSDT = Double.valueOf(account.getAssetBalance("USDT").getFree());
    }
    // Calculate and round the values in preparation for buying back
    Double freeUSDTFloored = CalcUtils.floorTo(freeUSDT);
    Double toToBuyFloored = CalcUtils.floorTo(freeUSDTFloored / buyPrice);
    try {
      logger.info(
          "Executing buy with: "
              + freeUSDTFloored
              + " USDT @ $"
              + buyPrice
              + " = "
              + toToBuyFloored
              + " "+to);
      // Submit the Binance buy back
      NewOrderResponse performBuy =
          client.newOrder(
              limitBuy(
                  ticker,
                  TimeInForce.GTC,
                  toToBuyFloored.toString(),
                  buyPrice.toString()));
      logger.info("Trade submitted: " + performBuy.getTransactTime());
    } catch (Exception e) {
      logger.error("There was an exception thrown during the buy?: " + e.getMessage());
    }
    new CalcUtils().sleeper(3000);
  }

  /** Execute a market buy back */
  private void executeMarketBuyBack() {
    // Cancel all open orders
    List<Order> openOrders = client.getOpenOrders(new OrderRequest(ticker));
    for (Order order : openOrders) {
      logger.info("Cancelling order: " + order.getOrderId());
      client.cancelOrder(new CancelOrderRequest(ticker, order.getOrderId()));
    }
    // Execute market buy back
    new CalcUtils().sleeper(3000);
    Account account = client.getAccount();
    // Find out how much free asset there is to trade
    Double freeUSDTFloored =
        CalcUtils.floorTo(Double.valueOf(account.getAssetBalance("USDT").getFree()));
    Double lastPrice = getCurrentPrice();
    Double toToBuyFloored = CalcUtils.floorTo(freeUSDTFloored / lastPrice);
    String message = "Executing market buy back of " + toToBuyFloored + " "+to+" @ $" + lastPrice;
    logger.info(message);
    sendTweet(message);
    client.newOrder(marketBuy(ticker, toToBuyFloored.toString()));
    new CalcUtils().sleeper(15000);
  }

  /**
   * Function to send a tweet. Pass in the message to send and it will use the preconfigured Twitter
   * OAuth credentials.
   *
   * @param message The message to tweet
   */
  private void sendTweet(String message) {
    // Use OAuth to pass Twitter credentials
    ConfigurationBuilder cb = new ConfigurationBuilder();
    cb.setDebugEnabled(true)
        .setOAuthConsumerKey(consumerKey)
        .setOAuthConsumerSecret(consumerSecret)
        .setOAuthAccessToken(accessToken)
        .setOAuthAccessTokenSecret(accessTokenSecret);
    TwitterFactory tf = new TwitterFactory(cb.build());
    Twitter twitter = tf.getInstance();
    // Tweets can only be 280 characters long error if longer
    if (message.length() <= 280) {
      try {
        if (EXECUTE_TWEETS) {
          twitter.updateStatus(message);
          // My bad I was sending a tweet
          logger.trace("Sent tweet to @WestworldWyatt");
        } else {
          logger.trace("No Twitter credentials have been set, not tweeting.");
        }
      } catch (TwitterException e) {
        logger.error("ERROR SENDING TWEET: Reason: {}", e);
      }
    } else {
      logger.error("Tweet too long!! (That's what she said)");
    }
  }

  /** Report that the system is in developer mode */
  private void reportDevMode() {
    logger.trace("Wyatt is currently in development mode! Not performing trades or tweets");
  }
}
