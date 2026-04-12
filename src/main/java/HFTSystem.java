import java.util.ArrayList;
import java.util.List;

/**
 * JavaHFT Quantitative Trading System
 *
 * Main orchestrator that connects:
 * - Real market data (Yahoo Finance)
 * - Quantitative trading strategies (Momentum, Mean Reversion, VWAP)
 * - Order Book matching engine
 * - Backtesting with P&L tracking
 * - Chart generation (PNG + ASCII)
 *
 * Usage: java HFTSystem [SYMBOL] [RANGE]
 *   SYMBOL: stock ticker (default: AAPL)
 *   RANGE:  1mo, 3mo, 6mo, 1y, 2y (default: 6mo)
 *
 * @author Anthony Lewallen
 */
public class HFTSystem {

    private static final double INITIAL_CAPITAL = 100_000.00;
    private static final String CHART_DIR = "charts";

    public static void main(String[] args) {

        // Parse command line args
        String symbol = args.length > 0 ? args[0].toUpperCase() : "AAPL";
        String range = args.length > 1 ? args[1] : "6mo";

        printBanner();
        System.out.printf("  Symbol: %s | Range: %s | Capital: $%,.2f\n\n", symbol, range, INITIAL_CAPITAL);

        // === STEP 1: Fetch Real Market Data ===
        System.out.println("[1/5] Fetching market data from Yahoo Finance...");
        MarketDataFetcher fetcher = new MarketDataFetcher();
        List<StockQuote> quotes;
        try {
            quotes = fetcher.fetchHistoricalData(symbol, range);
        } catch (Exception e) {
            System.err.println("ERROR: Failed to fetch market data: " + e.getMessage());
            System.err.println("Check your internet connection and verify the symbol is valid.");
            return;
        }

        if (quotes.isEmpty()) {
            System.err.println("ERROR: No data returned for " + symbol);
            return;
        }

        System.out.printf("  Fetched %d data points (%s to %s)\n",
                quotes.size(), quotes.get(0).getDate(), quotes.get(quotes.size() - 1).getDate());

        // Print latest market snapshot
        StockQuote latest = quotes.get(quotes.size() - 1);
        StockQuote previous = quotes.size() > 1 ? quotes.get(quotes.size() - 2) : latest;
        double dayChange = latest.getClose() - previous.getClose();
        double dayChangePct = dayChange / previous.getClose() * 100;

        System.out.println("\n  --- LATEST MARKET DATA ---");
        System.out.printf("  Date:    %s\n", latest.getDate());
        System.out.printf("  Open:    $%.2f\n", latest.getOpen());
        System.out.printf("  High:    $%.2f\n", latest.getHigh());
        System.out.printf("  Low:     $%.2f\n", latest.getLow());
        System.out.printf("  Close:   $%.2f (%s$%.2f / %.2f%%)\n",
                latest.getClose(),
                dayChange >= 0 ? "+" : "",
                dayChange, dayChangePct);
        System.out.printf("  Volume:  %,d\n", latest.getVolume());

        // === STEP 2: Initialize Strategies ===
        System.out.println("\n[2/5] Initializing trading strategies...");
        List<TradingStrategy> strategies = new ArrayList<>();
        strategies.add(new MovingAverageCrossoverStrategy(10, 50));
        strategies.add(new MeanReversionStrategy(20, 2.0));
        strategies.add(new VWAPStrategy(20, 0.02));

        for (TradingStrategy s : strategies) {
            System.out.printf("  - %s\n", s.getName());
        }

        // === STEP 3: Run Backtests ===
        System.out.println("\n[3/5] Running backtests...");
        BacktestEngine engine = new BacktestEngine(INITIAL_CAPITAL);
        List<BacktestEngine.BacktestResult> results = engine.compareStrategies(strategies, quotes, symbol);

        // === STEP 4: Generate Reports ===
        System.out.println("\n[4/5] Generating performance reports...");
        for (BacktestEngine.BacktestResult result : results) {
            result.printReport();
        }

        // Print strategy comparison table
        printComparisonTable(results);

        // === STEP 5: Generate Charts ===
        System.out.println("\n[5/5] Generating charts...");
        try {
            for (BacktestEngine.BacktestResult result : results) {
                String safeName = result.strategyName.replaceAll("[^a-zA-Z0-9]", "_");
                ChartRenderer.generatePriceChart(result,
                        CHART_DIR + "/" + symbol + "_" + safeName + "_price.png");
                ChartRenderer.generateEquityChart(result,
                        CHART_DIR + "/" + symbol + "_" + safeName + "_equity.png");
            }
            ChartRenderer.generateComparisonChart(results,
                    CHART_DIR + "/" + symbol + "_strategy_comparison.png");
        } catch (Exception e) {
            System.out.println("  PNG chart generation failed (headless environment?): " + e.getMessage());
            System.out.println("  Falling back to ASCII charts...");
        }

        // Always print ASCII charts to terminal
        for (BacktestEngine.BacktestResult result : results) {
            System.out.printf("\n--- %s ---", result.strategyName);
            ChartRenderer.printAsciiPriceChart(result.quotes, result.signals, 80);
            ChartRenderer.printAsciiEquityCurve(result.portfolio, 80);
        }

        // === FINAL: Current Recommendation ===
        printCurrentRecommendation(strategies, quotes, symbol);

        System.out.println("\n=== JavaHFT System Complete ===");
        System.out.println("Built by Anthony E. Lewallen");
        System.out.println("GitHub: https://github.com/LewallenAE/JavaHFT");
    }

    private static void printCurrentRecommendation(List<TradingStrategy> strategies,
                                                     List<StockQuote> quotes, String symbol) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  CURRENT TRADING RECOMMENDATION");
        System.out.println("=".repeat(60));

        int buyVotes = 0, sellVotes = 0, holdVotes = 0;
        double totalConfidence = 0;

        for (TradingStrategy strategy : strategies) {
            TradingSignal signal = strategy.evaluate(quotes, quotes.size() - 1, symbol);
            System.out.printf("\n  [%s]\n", strategy.getName());
            System.out.printf("    Action:     %s\n", signal.getAction());
            System.out.printf("    Confidence: %.0f%%\n", signal.getConfidence() * 100);
            System.out.printf("    Reason:     %s\n", signal.getReason());

            switch (signal.getAction()) {
                case BUY -> { buyVotes++; totalConfidence += signal.getConfidence(); }
                case SELL -> { sellVotes++; totalConfidence -= signal.getConfidence(); }
                case HOLD -> holdVotes++;
            }
        }

        System.out.println("\n  --- CONSENSUS ---");
        System.out.printf("  BUY votes: %d | SELL votes: %d | HOLD votes: %d\n",
                buyVotes, sellVotes, holdVotes);

        String consensus;
        if (buyVotes > sellVotes && buyVotes > holdVotes) {
            consensus = "BUY";
        } else if (sellVotes > buyVotes && sellVotes > holdVotes) {
            consensus = "SELL";
        } else {
            consensus = "HOLD";
        }

        StockQuote latest = quotes.get(quotes.size() - 1);
        System.out.printf("\n  >>> RECOMMENDATION: %s %s @ $%.2f <<<\n",
                consensus, symbol, latest.getClose());
        System.out.println("\n  DISCLAIMER: This is for educational purposes only.");
        System.out.println("  Not financial advice. Past performance != future results.");
        System.out.println("=".repeat(60));
    }

    private static void printComparisonTable(List<BacktestEngine.BacktestResult> results) {
        System.out.println("\n" + "=".repeat(90));
        System.out.println("  STRATEGY COMPARISON");
        System.out.println("=".repeat(90));
        System.out.printf("  %-30s %10s %10s %10s %8s %8s\n",
                "Strategy", "Return", "Benchmark", "Alpha", "Sharpe", "MaxDD");
        System.out.println("  " + "-".repeat(86));

        for (BacktestEngine.BacktestResult r : results) {
            System.out.printf("  %-30s %9.2f%% %9.2f%% %9.2f%% %8.2f %7.2f%%\n",
                    r.strategyName,
                    r.totalReturn * 100,
                    r.benchmarkReturn * 100,
                    (r.totalReturn - r.benchmarkReturn) * 100,
                    r.sharpeRatio,
                    r.portfolio.getMaxDrawdown() * 100
            );
        }
        System.out.println("=".repeat(90));
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("     JavaHFT - Quantitative Trading System");
        System.out.println("     High-Frequency Trading Engine + Market Analysis");
        System.out.println("     by Anthony E. Lewallen");
        System.out.println("=".repeat(60));
    }
}
