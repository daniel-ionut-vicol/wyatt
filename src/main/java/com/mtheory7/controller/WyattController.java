package com.mtheory7.controller;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mtheory7.wyatt.mind.Wyatt;
import com.mtheory7.wyatt.utils.CalcUtils;

@RestController
public class WyattController {

  private static final Logger logger = Logger.getLogger(WyattController.class);
  private static final String PATH_PROFIT = "/balance/profit";
  private static final String PATH_STATUS = "/status";
  private static final String PATH_ADD = "/add";
  private static final String PATH_REMOVE = "/remove";
  private static final String PATH_ORDER_HISTORY = "/orders";
  private static final String RESPONSE_SUFFIX = " endpoint hit";
  private final Wyatt wyatt;
  public int serverPort;
  public String hostIP;
  public String mainColor;

  @Autowired
  public WyattController(Wyatt wyatt) {
    this.wyatt = wyatt;
  }

  @Value("${mainColor}")
  public void getMainColor(String color) {
    this.mainColor = color;
  }

  @Value("${server.port}")
  public void getServerPort(int port) {
    this.serverPort = port;
  }

  @Value("${hostIP}")
  public void getHostIP(String ip) {
    this.hostIP = ip;
  }

  @GetMapping(path = PATH_PROFIT)
  public ResponseEntity<String> getTotalProfit() {
    logger.trace(PATH_PROFIT + RESPONSE_SUFFIX);
    return new ResponseEntity<>(wyatt.getCurrentProfit(), HttpStatus.OK);
  }

  @GetMapping(path = PATH_REMOVE )
  public ResponseEntity<StringBuilder> remove(@RequestParam(required = true) String ticker) throws InterruptedException {
	 wyatt.removeAgent(ticker);
	 return getState();
  }
  
  @PostMapping(path = PATH_ADD )
  public ResponseEntity<StringBuilder> add(@RequestParam(required = true) String from, @RequestParam(required = true) String to, @RequestParam(required = true) Double initialInvestment, @RequestParam(defaultValue = "true") boolean development) {
	  wyatt.addAgent(from, to, initialInvestment, development);
	 return getState();
  }
  
  @GetMapping(path = PATH_STATUS)
  public ResponseEntity<StringBuilder> getState() {
    StringBuilder response =
        new StringBuilder(
            "M\"\"MMM\"\"MMM\"\"M&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;dP&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;dP<br>M&nbsp;&nbsp;MMM&nbsp;&nbsp;MMM&nbsp;&nbsp;M&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;88&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;88<br>M&nbsp;&nbsp;MMP&nbsp;&nbsp;MMP&nbsp;&nbsp;M&nbsp;dP&nbsp;&nbsp;&nbsp;&nbsp;dP&nbsp;.d8888b.&nbsp;d8888P&nbsp;d8888P<br>M&nbsp;&nbsp;MM'&nbsp;&nbsp;MM'&nbsp;.M&nbsp;88&nbsp;&nbsp;&nbsp;&nbsp;88&nbsp;88'&nbsp;&nbsp;`88&nbsp;&nbsp;&nbsp;88&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;88<br>M&nbsp;&nbsp;`'&nbsp;.&nbsp;''&nbsp;.MM&nbsp;88.&nbsp;&nbsp;.88&nbsp;88.&nbsp;&nbsp;.88&nbsp;&nbsp;&nbsp;88&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;88<br>M&nbsp;&nbsp;&nbsp;&nbsp;.d&nbsp;&nbsp;.dMMM&nbsp;`8888P88&nbsp;`88888P8&nbsp;&nbsp;&nbsp;dP&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;dP<br>MMMMMMMMMMMMMM&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;.88<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;d8888P<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
    response.append(wyatt.getStatus());
    response.append("<br><br>--- Links ---");
    response.append(
        "<br><a href=\"https://github.com/mtheory7/wyatt\" style=\"color:" + this.mainColor + "\">Source Code</a>");
    response.append(
        "<br><a href=\"https://twitter.com/WestworldWyatt\" style=\"color:" + this.mainColor + "\">Twitter</a>");
    
    for(String ticker : wyatt.tickers()) {
	    response.append(
	        "<br><a href=\"http://"
	            + this.hostIP
	            + ":"
	            + this.serverPort
	            + "/orders?ticker="+ticker+"\" style=\"color:" + this.mainColor + "\">Order History  " + ticker+ "</a>");
	    response.append(    "---------- <a href=\"http://"
        + this.hostIP
        + ":"
        + this.serverPort
        + PATH_REMOVE+"?ticker="+ticker+"\" style=\"color:" + this.mainColor + "\">Remove" + ticker+ "</a>");
    }
    String action = "\"http://"
            + this.hostIP
            + ":"
            + this.serverPort
            +PATH_ADD+"\"";
    response.append("<form action="+action+" method=\"post\">"
    		+"<input name=\"from\" placeholder=\"SHIB\"/>"
    		+"<input name=\"to\" placeholder=\"USDT\"/>"
    		+"<input name=\"initialInvestment\" placeholder=\"10\"/>"
    		+"<input name=\"development\" placeholder=\"true\"/>"
    		+"<input type=\"submit\" value=\"Add\"/>"
    		+ "</form>");
    
    response.append("<br>Uptime: ").append(CalcUtils.getUpTimeString()).append("</g>");
    return new ResponseEntity<StringBuilder>(
        new StringBuilder(
                "<html><head><link rel=\"apple-touch-icon\" sizes=\"180x180\" href=\"https://"
                    + this.hostIP
                    + "/apple-touch-icon.png\"><link rel=\"icon\" type=\"image/png\" sizes=\"32x32\" href=\"https://"
                    + this.hostIP
                    + "/favicon-32x32.png\"><link rel=\"icon\" type=\"image/png\" sizes=\"16x16\" href=\"https://"
                    + this.hostIP
                    + "/favicon-16x16.png\"><link rel=\"manifest\" href=\"https://"
                    + this.hostIP
                    + "/site.webmanifest\"><link rel=\"mask-icon\" href=\"https://"
                    + this.hostIP
                    + "/safari-pinned-tab.svg\" color=\"#5bbad5\"><meta name=\"msapplication-TileColor\" content=\"#da532c\"><meta name=\"theme-color\" content=\"#ffffff\"><meta http-equiv=\"refresh\" content=\"25\" /><style>body {  color: " + this.mainColor + ";}m {  color: #A9A9A9;}g {  color: #999999;}</style></head><title>Wyatt</title><body bgcolor=\"#000000\"><font face=\"Courier\" size=\"3\">")
            .append(response)
            .append("</font></body></html>"),
        HttpStatus.OK);
  }

  @GetMapping(path = PATH_ORDER_HISTORY)
  public ResponseEntity<String> getOrderHistory(@RequestParam final String ticker) {
    logger.trace(PATH_ORDER_HISTORY + RESPONSE_SUFFIX);
    String response = wyatt.getOrderHistory(ticker);
    return new ResponseEntity<>(
        "<html>"
            + "<head>"
            + "<link rel=\"apple-touch-icon\" sizes=\"180x180\" href=\"https://"
            + this.hostIP
            + "/apple-touch-icon.png\">"
            + "<link rel=\"icon\" type=\"image/png\" sizes=\"32x32\" href=\"https://"
            + this.hostIP
            + "/favicon-32x32.png\">"
            + "<link rel=\"icon\" type=\"image/png\" sizes=\"16x16\" href=\"https://"
            + this.hostIP
            + "/favicon-16x16.png\">"
            + "<link rel=\"manifest\" href=\"https://"
            + this.hostIP
            + "/site.webmanifest\">"
            + "<link rel=\"mask-icon\" href=\"https://"
            + this.hostIP
            + "/safari-pinned-tab.svg\" color=\"#5bbad5\">"
            + "<meta name=\"msapplication-TileColor\" content=\"#da532c\">"
            + "<meta name=\"theme-color\" content=\"#ffffff\">"
            + "<meta http-equiv=\"refresh\" content=\"25\" />"
            + "</head>"
            + "<title>Wyatt</title>"
            + "<body bgcolor=\"#000000\">"
            + "<font face=\"Courier\" size=\"3\" color=\"" + this.mainColor + "\">"
            + "<a href=\"http://"
            + this.hostIP
            + ":"
            + this.serverPort
            + "/status\" style=\"color:" + this.mainColor + "\">Back</a>"
            + response
            + "</font>"
            + "</body>"
            + "</html>",
        HttpStatus.OK);
  }

}
