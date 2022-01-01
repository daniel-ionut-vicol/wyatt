package com.mtheory7.wyatt.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class Wyatt {

	private static final Logger logger = Logger.getLogger(Wyatt.class);

	@Value("${buyBackAfterThisPercentage}")
	private Double buyBackAfterThisPercentage = 0.993;
	@Value("${sellPriceMultiplier}")
	private Double sellPriceMultiplier = 1.025;

	private String binanceAPIKey;
	private String binanceAPISecret;
	
	private Map<String, WyattAgent> agents=new HashMap<>();
	
	public Wyatt() {
	}
	
	public void setBinanceCreds(String binanceAPIKey, String binanceAPISecret) {
		logger.trace("Setting Binance credentials");
		this.binanceAPIKey=binanceAPIKey;
		this.binanceAPISecret=binanceAPISecret;
	}
	
	public boolean addAgent(String from, String to, Double initialInvestment, boolean development) {
		WyattAgent agent = new WyattAgent();
		agent.setTicker(to, from);
		agent.setInitialInvestment(initialInvestment);
		if(agents.get(agent.getTicker())!=null) {
			return false;
		}
		agent.setBinanceCreds(binanceAPIKey, binanceAPISecret);
		agents.put(agent.getTicker(), agent);
		agent.start();
		return true;
	}
	
	public boolean removeAgent(String ticker) throws InterruptedException {
		WyattAgent agent = agents.get(ticker);
		if(agent!=null) {
			agent.stopAgent();
			agent.join();
			return true;
		}
		return false;
	}
	
	public String getCurrentProfit() {
		Double total = new Double(0);
		for(String ticker:agents.keySet()) {
			total = total + getCurrentProfit(ticker);
		}
		return total.toString();
	}
	
	public Double getCurrentProfit(String ticker) {
		WyattAgent agent = agents.get(ticker);
		if(agent!=null) {
			return agent.getCurrentProfit();
		}
		return (double) 0;
	}

	public Double getInitialInvestmentUSD() {
		Double total = new Double(0);
		for(String ticker:agents.keySet()) {
			total = total + getInitialInvestmentUSD(ticker);
		}
		return total;
	}
	
	public Double getInitialInvestmentUSD(String ticker) {
		WyattAgent agent = agents.get(ticker);
		if(agent!=null) {
			return agent.getInitialInvestmentUSD();
		}
		return (double) 0;
	}
	
	public Double getCurrentBalance() {
		Double total = new Double(0);
		for(String ticker:agents.keySet()) {
			total = total + getInitialInvestmentUSD(ticker);
		}
		return total;
	}
	
	public Double getCurrentBalance(String ticker) {
		WyattAgent agent = agents.get(ticker);
		if(agent!=null) {
			return agent.getCurrentBalance();
		}
		return (double) 0;
	}
	
	public String getStatus() {
		StringBuilder sb = new StringBuilder();
		for(String ticker:agents.keySet()) {
			sb.append(getStatus(ticker)).append("<br>");
		}
		return sb.toString();
	}
	
	public String getStatus(String ticker) {
		WyattAgent agent = agents.get(ticker);
		if(agent==null) {
			return "";
		}
	    Double initialUSD = agent.getInitialInvestmentUSD();
	    Double currentBalance = agent.getCurrentBalance();
	    Double USDProfit = currentBalance - initialUSD;
		StringBuilder response = new StringBuilder();
		response.append("<br>--- Status report " + ticker +"---");
	    response.append("<br>Status: ").append(agent.getCurrentStateString());
	    response
	    	.append("<br>Total(USD): $")
	    	.append(String.format("%.2f", currentBalance))
	    	.append("<br>Initial(USD): $")
	    	.append(String.format("%.2f", initialUSD))
	    	.append("<br>Profit(USD): $")
	        .append(String.format("%.2f", USDProfit))
	        .append(" (")
	        .append(String.format("%.3f", (USDProfit / initialUSD * 100)))
	        .append("%)");
	    response.append(agent.getBalances());
	    response.append("<br><br>--- Market ---");
	    response.append("<br>Target: $").append(String.format("%.17f", agent.getCurrentTargetPrice()));
	    response
	        .append("<br>Buy back: $")
	        .append(String.format("%.17f", agent.getCurrentBuyBackPrice()));
	    response.append("<br>Sell confidence: ").append(agent.getCurrentSellConfidence()).append("%");
	    if (!agent.sell) {
	      Double diff = agent.getCurrentPrice() - agent.getOpenBuyBackPrice();
	      response.append("<br><br>--- Open buy back ---");
	      response
	          .append("<br>Amount: ")
	          .append(agent.getOpenBuyBackAmt())
	          .append(" "+agent.to+" @ $")
	          .append(String.format("%.17f", agent.getOpenBuyBackPrice()));
	      response
	          .append("<br>Difference: $")
	          .append(String.format("%.17f", diff))
	          .append(" (")
	          .append(agent.getOpenBuyBackPercentage())
	          .append("%)");
	    }
	    return response.toString();
	}
	
	public String getOrderHistory(String ticker) {
		WyattAgent agent = agents.get(ticker);
		if(agent!=null) {
			return agent.getOrderHistory();
		}
		return "";
	}
	
	public Set<String> tickers(){
		return agents.keySet(); 
	}
	
	
	
}
