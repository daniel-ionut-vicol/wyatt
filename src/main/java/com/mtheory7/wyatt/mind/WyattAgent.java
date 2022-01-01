package com.mtheory7.wyatt.mind;

import static com.binance.api.client.domain.account.NewOrder.limitBuy;
import static com.binance.api.client.domain.account.NewOrder.limitSell;
import static com.binance.api.client.domain.account.NewOrder.marketBuy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import com.mtheory7.wyatt.model.DataIdentifier;
import com.mtheory7.wyatt.model.data.MindData;
import com.mtheory7.wyatt.model.data.PredictionEngine;
import com.mtheory7.wyatt.utils.CalcUtils;

@Service
public class WyattAgent extends Thread {

	private static final Logger logger = Logger.getLogger(WyattAgent.class);
	private static final CandlestickInterval[] intervalList = { CandlestickInterval.ONE_MINUTE,
			CandlestickInterval.THREE_MINUTES, CandlestickInterval.FIVE_MINUTES, CandlestickInterval.FIFTEEN_MINUTES };

	public String from;
	public String to;
	public boolean development_mode;
	public boolean sell = true;
	private Double lastTargetPrice = 1000000.0;
	private Double buyBackPrice = 0.0;
	private Double openBuyBackPrice = 0.0;
	private Double openBuyBackAmt = 0.0;
	private MindData mindData;
	private PredictionEngine predictionEngine;
	private BinanceApiRestClient client;

	private Double initial_investment;
	private Double buyBackAfterThisPercentage;
	private Double sellPriceMultiplier;

	private volatile boolean stop = false;

	@Override
	public void run() {
		while (!stop) {
			try {
				gatherMindData();
				predictAndTrade();
			} catch (Exception e) {
				logger.error("There was an error during the main trading loop! {}", e);
			} finally {
				reset();
				new CalcUtils().sleeper(25000);
			}
		}
	}

	public void stopAgent() {
		stop = true;
	}

	public void setDev(boolean dev) {
		development_mode= dev;
	}
	
	public void setTicker(String to, String from) {
		this.to = to;
		this.from = from;
	}

	public String getTicker() {
		return to + from;
	}

	/**
	 * Resets Wyatt's memory. This is necessary to do or memory leaks will be
	 * possible
	 */
	public void reset() {
		this.mindData = new MindData();
		this.predictionEngine = new PredictionEngine(buyBackAfterThisPercentage, sellPriceMultiplier);
	}

	/**
	 * Returns the current state of Wyatt. This relates to whether the bot is
	 * waiting for a buy back or not
	 *
	 * @return String for the UI stating Wyatt's status
	 */
	public String getCurrentStateString() {
		if (sell) {
			return "Waiting to sell";
		} else {
			return "Waiting for buy back";
		}
	}

	/**
	 * Returns the current price according to Binance
	 *
	 * @return Current price in Double form
	 */
	public Double getCurrentPrice() {
		TickerStatistics tickerStatistics = client.get24HrPriceStatistics(getTicker());
		return Double.valueOf(tickerStatistics.getLastPrice());
	}

	public Double getCurrentPrice(String ticker) {
		TickerStatistics tickerStatistics = client.get24HrPriceStatistics(ticker);
		return Double.valueOf(tickerStatistics.getLastPrice());
	}

	public void setInitialInvestment(double initialInvestment) {
		this.initial_investment=initialInvestment;
	}
	
	public Double getInitialInvestmentUSD() {
		return initial_investment;
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
	 * Returns the order history to the UI for displaying in the /orders endpoint
	 *
	 * @return String (HTML) of the order history
	 */
	public String getOrderHistory() {
		String response = "";
		List<Trade> trades = client.getMyTrades(getTicker());
		for (Trade trade : trades) {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/dd/yyyy' 'HH:mm:ss:S");
			response = new StringBuilder().append("<br><font color=\"").append(trade.isBuyer() ? "green" : "red")
					.append("\">").append(trade.getOrderId()).append(": Date/Time: ")
					.append(simpleDateFormat.format(trade.getTime())).append(": ").append(trade.getQty())
					.append(" " + getTicker() + " @ $").append(String.format("%.2f", Double.valueOf(trade.getPrice())))
					.append("</font>").append(response).toString();
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
					response.append("<br>&nbsp;&nbsp;-&nbsp;").append(amount).append(" ").append(balance.getAsset());
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
	 * @param binanceAPIKey    Binance API Key
	 * @param binanceAPISecret Binance API Secret
	 */
	public void setBinanceCreds(String binanceAPIKey, String binanceAPISecret) {
		logger.trace("Setting Binance credentials");
		mindData = new MindData();
		predictionEngine = new PredictionEngine(buyBackAfterThisPercentage, sellPriceMultiplier);
		BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(binanceAPIKey, binanceAPISecret);
		client = factory.newRestClient();
	}

	/**
	 * Returns the total balance of the account in current estimated usd
	 *
	 * @return Balance in usd
	 */
	public Double getCurrentBalance() {
		Account account = client.getAccount();
		// Pull the latest account balance info from Binance
		List<AssetBalance> balances = account.getBalances();
		Double estimatedBalance = 0.0;
		for (AssetBalance balance : balances) {
			// only calculate for what we are trading
			if (getAssetName(balance.getAsset()).equals(to) || getAssetName(balance.getAsset()).equals(from)) {
				Double amount = Double.valueOf(balance.getFree()) + Double.valueOf(balance.getLocked());
				if (amount > 0.0) {
					String asset = getAssetName(balance.getAsset());
					estimatedBalance += valueInUSD(amount, asset);
				}
			}
		}
		estimatedBalance = CalcUtils.roundTo(estimatedBalance);
		return estimatedBalance;
	}

	/**
	 * Return the current profit from the starting investment
	 *
	 * @return The current profit
	 */
	public Double getCurrentProfit() {
		Double percentOnInvenstment = CalcUtils
				.roundTo((((Double.valueOf(getCurrentBalance()) / getInitialInvestmentUSD()) * 100) - 100));
		return percentOnInvenstment;
	}

	/**
	 * Estimate the value of a given amount/ticker in USD
	 *
	 * @param amount The amount of an asset
	 * @param ticker The ticker of the asset to estimate
	 */
	private Double valueInUSD(Double amount, String ticker) {
		if (ticker.equals("USDT")) {
			return amount;
		} else {
			double value = 0;
			try {
				value = Double.valueOf(client.get24HrPriceStatistics(ticker + "USDT").getLastPrice()) * amount;
			} catch (BinanceApiException e) {
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
	 * Retrieves data from the ticker data pulled from Binance. This data is then
	 * used later for predicting a selling price.
	 */
	public void gatherMindData() {
		for (CandlestickInterval interval : intervalList) {
			gatherIntervalData(mindData, interval, getTicker());
			new CalcUtils().sleeper(500);
		}
	}

	/**
	 * Use the gathered data to attempt to predict a price to sell at, and then a
	 * price to buy back at. When the price exceeds that target value, perform a
	 * sell and a buy back to make an incremental amount of money.
	 */
	public void predictAndTrade() {
		if (development_mode) {
			reportDevMode();
		}
		// Gather data calculate, and update Wyatt's target price and buy back
		predictionEngine.executeThoughtProcess(mindData, getTicker());
		lastTargetPrice = predictionEngine.targetPrice;
		// Find current price and decide to sell
		Double currentPrice = CalcUtils.roundTo(getCurrentPrice());
		buyBackPrice = CalcUtils.roundTo(currentPrice * predictionEngine.buyBackAfterThisPercentage);
		if (currentPrice > lastTargetPrice) {
			lastTargetPrice = currentPrice;
			predictionEngine.targetPrice = currentPrice;
		}
		double sellConfidence = CalcUtils.roundTo((currentPrice / predictionEngine.targetPrice * 100));
		logger.trace("Current: $" + currentPrice + " Target: $" + predictionEngine.targetPrice + " Buy back: $"
				+ buyBackPrice + " Sell confidence: " + sellConfidence + "%");
		List<Order> openOrders = client.getOpenOrders(new OrderRequest(getTicker()));
		if (!openOrders.isEmpty()) {
			sell = false;
			logger.trace("Number of open " + getTicker() + " orders: " + openOrders.size());
			Order openOrder = openOrders.get(0);
			if (openOrder != null) {
				openBuyBackAmt = Double.valueOf(openOrder.getOrigQty());
				openBuyBackPrice = Double.valueOf(openOrder.getPrice());
				Double currentMargin = currentPrice / Double.valueOf(openOrder.getPrice());
				Double currentMarginPercent = CalcUtils.roundTo((currentMargin - 1) * 100);
				Double buyBackDifference = CalcUtils.roundTo((currentPrice - Double.valueOf(openOrder.getPrice())));
				logger.trace("Current buy back: " + currentMarginPercent + "% ($" + buyBackDifference + ")");
				if (currentMarginPercent > 10 || (System.currentTimeMillis() - openOrder.getTime()) > 432000000) {
					logger.trace("Deciding to submit a market buy back at $" + currentPrice);
					if (!development_mode) {
						executeMarketBuyBack();
					} else {
						reportDevMode();
					}
				} else {
					logger.trace("Orders for " + getTicker() + " are not empty, not trading for 120 seconds...");
					new CalcUtils().sleeper(120000);
				}
			}
		} else {
			sell = true;
		}
		if (((currentPrice >= lastTargetPrice) || ((currentPrice / lastTargetPrice) < 0.80)) && sell) {
			// Find out how much free asset there is to trade
			Account account = client.getAccount();
			Double freeToFloored = CalcUtils.floorTo(Double.valueOf(account.getAssetBalance(to).getFree()));
			String message = "Selling " + freeToFloored + " " + to + " at $" + String.format("%.2f", currentPrice)
					+ " and buying back at $" + String.format("%.2f", buyBackPrice);
			logger.info(message);
			if (!development_mode) {
				performSellAndBuyBack(currentPrice, buyBackPrice, message);
			} else {
				reportDevMode();
			}
		}
	}

	/**
	 * Function to connect to Binance, and pull the information in candle form used
	 * to run the bot.
	 *
	 * @param mindData The structure to save the data to
	 * @param interval The interval to grab candle data for
	 * @param ticker   The ticker to grab candle data for
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
		mindData.lastPriceData.put(new DataIdentifier(interval, ticker), ts);
		mindData.candlestickIntAvgData.put(new DataIdentifier(interval, ticker),
				new CalcUtils().findAveragePrice(candlesticks));
	}

	/**
	 * Perform a sell and buy at the passed in values. Uses the Binance
	 * configuration to execute these trades.
	 *
	 * @param sellPrice Price to sell at
	 * @param buyPrice  Price to buy at
	 */
	private void performSellAndBuyBack(Double sellPrice, Double buyPrice, String message) {
		Account account = client.getAccount();
		// Find out how much free asset there is to trade
		Double freeToFloored = CalcUtils.floorTo(Double.valueOf(account.getAssetBalance(to).getFree()));
		logger.info("Amount of " + to + " to trade: " + freeToFloored);
		try {
			logger.info("Executing sell of: " + freeToFloored + " " + to + " @ $" + sellPrice);
			// Submit the binance sell
			NewOrderResponse performSell = client
					.newOrder(limitSell(getTicker(), TimeInForce.GTC, freeToFloored.toString(), sellPrice.toString()));
			logger.info("Trade submitted: " + performSell.getTransactTime());
			logger.trace("Switching currentState to false - Awaiting buy back");
			sell = false;
		} catch (Exception e) {
			logger.error("There was an exception thrown during the sell?: " + e.getMessage());
		}
		new CalcUtils().sleeper(3000);
		// Wait and make sure that the trade executed. If not, keep waiting
		List<Order> openOrders = client.getOpenOrders(new OrderRequest(getTicker()));
		logger.trace("Number of open " + getTicker() + " orders: " + openOrders.size());
		while (!openOrders.isEmpty()) {
			logger.trace("Orders for " + getTicker() + " are not empty, waiting 3 seconds...");
			new CalcUtils().sleeper(3000);
			openOrders = client.getOpenOrders(new OrderRequest(getTicker()));
		}
		new CalcUtils().sleeper(3000);
		// Verify that we have the correct amount of asset to trade
		account = client.getAccount();
		Double freeUSDT = Double.valueOf(account.getAssetBalance("LDUSDT").getFree());
		// Loop until above 10.0 USDT
		while (freeUSDT < 10.0) {
			logger.trace("Looping because we currently have less than 10 USDT. Waiting 15 seconds...");
			new CalcUtils().sleeper(15000);
			account = client.getAccount();
			freeUSDT = Double.valueOf(account.getAssetBalance("LDUSDT").getFree());
		}
		// Calculate and round the values in preparation for buying back
		Double freeUSDTFloored = CalcUtils.floorTo(freeUSDT);
		Double toToBuyFloored = CalcUtils.floorTo(freeUSDTFloored / buyPrice);
		try {
			logger.info("Executing buy with: " + freeUSDTFloored + " USDT @ $" + buyPrice + " = " + toToBuyFloored + " "
					+ to);
			// Submit the Binance buy back
			NewOrderResponse performBuy = client
					.newOrder(limitBuy(getTicker(), TimeInForce.GTC, toToBuyFloored.toString(), buyPrice.toString()));
			logger.info("Trade submitted: " + performBuy.getTransactTime());
		} catch (Exception e) {
			logger.error("There was an exception thrown during the buy?: " + e.getMessage());
		}
		new CalcUtils().sleeper(3000);
	}

	/** Execute a market buy back */
	private void executeMarketBuyBack() {
		// Cancel all open orders
		List<Order> openOrders = client.getOpenOrders(new OrderRequest(getTicker()));
		for (Order order : openOrders) {
			logger.info("Cancelling order: " + order.getOrderId());
			client.cancelOrder(new CancelOrderRequest(getTicker(), order.getOrderId()));
		}
		// Execute market buy back
		new CalcUtils().sleeper(3000);
		Account account = client.getAccount();
		// Find out how much free asset there is to trade
		Double freeUSDTFloored = CalcUtils.floorTo(Double.valueOf(account.getAssetBalance("USDT").getFree()));
		Double lastPrice = getCurrentPrice();
		Double toToBuyFloored = CalcUtils.floorTo(freeUSDTFloored / lastPrice);
		String message = "Executing market buy back of " + toToBuyFloored + " " + to + " @ $" + lastPrice;
		logger.info(message);
		client.newOrder(marketBuy(getTicker(), toToBuyFloored.toString()));
		new CalcUtils().sleeper(15000);
	}

	/** Report that the system is in developer mode */
	private void reportDevMode() {
		logger.trace("Wyatt is currently in development mode! Not performing trades or tweets");
	}
}
