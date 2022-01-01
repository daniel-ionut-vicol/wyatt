package com.mtheory7;

import org.apache.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.mtheory7.wyatt.mind.Wyatt;

@SpringBootApplication
public class WyattApplication {
  private static final Logger logger = Logger.getLogger(WyattApplication.class);

  public static String B_API=null;
  public static String B_SECRET=null;
  
  public static void main(String[] args) {
    ConfigurableApplicationContext context = SpringApplication.run(WyattApplication.class, args);
    Wyatt dolores = context.getBean(Wyatt.class);
    if (args.length < 2) {
      logger.error("Too few arguments given!");
      System.exit(-1);
    }
    if (args.length == 2) {
      logger.info("2 arguments provided. Proceeding to set Binance credentials");
      B_API=args[0];
      B_SECRET=args[1];
      dolores.setBinanceCreds(args[0], args[1]);
    } else {
      logger.error("Incorrect number of arguments given!");
      System.exit(-1);
    }
  }
}
