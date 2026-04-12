import java.util.ArrayList;
import java.util.List;

/**
 * Backtesting engine that runs trading strategies against historical data.
 * Simulates order execution through the OrderBook matching engine.
 *
 * @author Anthony Lewallen
 */
public class BacktestEngine {

    private final double initialCapital;

    public BacktestEngine(double initialCapital) {
        this.initialCapital = initialCapital;
    }

    /**
     * Run a backtest for a single strategy on historical data.
     *
     * @param strategy the trading strategy to test
     * @param quotes   historical price data
     * @param symbol   the ticker symbol
     * @return BacktestResult with full performance metrics
     */
    public BacktestResult run(TradingStrategy strategy, List<StockQuote> quotes, String symbol) {
        PortfolioTracker portfolio = new PortfolioTracker(initialCapital);
        OrderBook orderBook = new OrderBook();
        List<TradingSignal> signals = new ArrayList<>();

        System.out.printf("\n=== BACKTESTING: %s on %s (%d bars) ===\n",
                strategy.getName(), symbol, quotes.size());

        for (int i = 0; i < quotes.size(); i++) {
            StockQuote quote = quotes.get(i);
            TradingSignal signal = strategy.evaluate(quotes, i, symbol);

            if (signal.getAction() != TradingSignal.Action.HOLD) {
                signals.add(signal);

                // Execute through the order book
                executeSignal(signal, quote, orderBook, portfolio);
            }

            // Record equity for charting
            portfolio.recordEquity(quote.getClose(), quote.getDate());
        }

        // Calculate final metrics
        StockQuote lastQuote = quotes.get(quotes.size() - 1);
        double finalValue = portfolio.getTotalValue(lastQuote.getClose());
        double totalReturn = (finalValue - initialCapital) / initialCapital;

        // Buy-and-hold benchmark
        StockQuote firstQuote = quotes.get(0);
        double benchmarkReturn = (lastQuote.getClose() - firstQuote.getClose()) / firstQuote.getClose();

        // Sharpe ratio (annualized, simplified)
        double sharpe = calculateSharpeRatio(portfolio.getEquityCurve());

        return new BacktestResult(
                strategy.getName(), symbol, signals, portfolio,
                initialCapital, finalValue, totalReturn,
                benchmarkReturn, sharpe, quotes
        );
    }

    /**
     * Run multiple strategies and compare results.
     */
    public List<BacktestResult> compareStrategies(List<TradingStrategy> strategies,
                                                   List<StockQuote> quotes, String symbol) {
        List<BacktestResult> results = new ArrayList<>();
        for (TradingStrategy strategy : strategies) {
            results.add(run(strategy, quotes, symbol));
        }
        return results;
    }

    private void executeSignal(TradingSignal signal, StockQuote quote,
                                OrderBook orderBook, PortfolioTracker portfolio) {
        double price = quote.getClose();

        switch (signal.getAction()) {
            case BUY -> {
                // Place a limit bid slightly above market to ensure fill
                Order buyOrder = new Order(Order.Type.BID, price * 1.001, signal.getQuantity());
                // Place a matching ask at market price (simulates market maker)
                Order matchAsk = new Order(Order.Type.ASK, price, signal.getQuantity());
                orderBook.addOrder(matchAsk);
                orderBook.addOrder(buyOrder);
                portfolio.buy(signal.getQuantity(), price, quote.getDate());
            }
            case SELL -> {
                if (portfolio.getPosition() > 0) {
                    int sellQty = Math.min(signal.getQuantity(), portfolio.getPosition());
                    Order sellOrder = new Order(Order.Type.ASK, price * 0.999, sellQty);
                    Order matchBid = new Order(Order.Type.BID, price, sellQty);
                    orderBook.addOrder(matchBid);
                    orderBook.addOrder(sellOrder);
                    portfolio.sell(sellQty, price, quote.getDate());
                }
            }
            default -> { }
        }
    }

    private double calculateSharpeRatio(List<PortfolioTracker.EquityPoint> equityCurve) {
        if (equityCurve.size() < 2) return 0;

        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < equityCurve.size(); i++) {
            double prevVal = equityCurve.get(i - 1).value;
            double currVal = equityCurve.get(i).value;
            if (prevVal > 0) {
                returns.add((currVal - prevVal) / prevVal);
            }
        }

        if (returns.isEmpty()) return 0;

        double avgReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(r -> (r - avgReturn) * (r - avgReturn))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) return 0;

        // Annualize (assuming ~252 trading days)
        return (avgReturn / stdDev) * Math.sqrt(252);
    }

    /** Contains all results from a backtest run */
    public static class BacktestResult {
        public final String strategyName;
        public final String symbol;
        public final List<TradingSignal> signals;
        public final PortfolioTracker portfolio;
        public final double initialCapital;
        public final double finalValue;
        public final double totalReturn;
        public final double benchmarkReturn;
        public final double sharpeRatio;
        public final List<StockQuote> quotes;

        public BacktestResult(String strategyName, String symbol, List<TradingSignal> signals,
                              PortfolioTracker portfolio, double initialCapital, double finalValue,
                              double totalReturn, double benchmarkReturn, double sharpeRatio,
                              List<StockQuote> quotes) {
            this.strategyName = strategyName;
            this.symbol = symbol;
            this.signals = signals;
            this.portfolio = portfolio;
            this.initialCapital = initialCapital;
            this.finalValue = finalValue;
            this.totalReturn = totalReturn;
            this.benchmarkReturn = benchmarkReturn;
            this.sharpeRatio = sharpeRatio;
            this.quotes = quotes;
        }

        public void printReport() {
            System.out.println("\n" + "=".repeat(60));
            System.out.printf("  BACKTEST REPORT: %s\n", strategyName);
            System.out.printf("  Symbol: %s | Bars: %d\n", symbol, quotes.size());
            System.out.println("=".repeat(60));

            portfolio.printSummary(quotes.get(quotes.size() - 1).getClose());

            System.out.printf("\nSharpe Ratio:      %.2f\n", sharpeRatio);
            System.out.printf("Benchmark (B&H):   %.2f%%\n", benchmarkReturn * 100);
            System.out.printf("Alpha:             %.2f%%\n", (totalReturn - benchmarkReturn) * 100);
            System.out.printf("Total Signals:     %d\n", signals.size());

            // Show last 5 signals
            int start = Math.max(0, signals.size() - 5);
            if (!signals.isEmpty()) {
                System.out.println("\nRecent Signals:");
                for (int i = start; i < signals.size(); i++) {
                    System.out.println("  " + signals.get(i));
                }
            }
            System.out.println("=".repeat(60));
        }
    }
}
