import java.util.List;

/**
 * Moving Average Crossover Strategy (Momentum).
 *
 * Classic quant strategy: generates BUY when the fast moving average
 * crosses above the slow moving average, and SELL when it crosses below.
 *
 * - Fast MA (default 10-day SMA): responsive to recent price action
 * - Slow MA (default 50-day SMA): captures longer-term trend
 * - Crossover = momentum shift
 *
 * @author Anthony Lewallen
 */
public class MovingAverageCrossoverStrategy implements TradingStrategy {

    private final int fastPeriod;
    private final int slowPeriod;

    public MovingAverageCrossoverStrategy() {
        this(10, 50);
    }

    public MovingAverageCrossoverStrategy(int fastPeriod, int slowPeriod) {
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("Fast period must be less than slow period");
        }
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
    }

    @Override
    public String getName() {
        return String.format("MA Crossover (%d/%d)", fastPeriod, slowPeriod);
    }

    @Override
    public TradingSignal evaluate(List<StockQuote> quotes, int currentIndex, String symbol) {
        // Need enough data for the slow MA + 1 previous bar for crossover detection
        if (currentIndex < slowPeriod) {
            return holdSignal(quotes.get(currentIndex), symbol, "Insufficient data for MA calculation");
        }

        double currentFastMA = calculateSMA(quotes, currentIndex, fastPeriod);
        double currentSlowMA = calculateSMA(quotes, currentIndex, slowPeriod);
        double prevFastMA = calculateSMA(quotes, currentIndex - 1, fastPeriod);
        double prevSlowMA = calculateSMA(quotes, currentIndex - 1, slowPeriod);

        StockQuote current = quotes.get(currentIndex);
        double price = current.getClose();

        // Golden Cross: fast MA crosses above slow MA
        if (prevFastMA <= prevSlowMA && currentFastMA > currentSlowMA) {
            double spread = (currentFastMA - currentSlowMA) / currentSlowMA;
            double confidence = Math.min(0.95, 0.6 + spread * 10);
            return new TradingSignal(
                    TradingSignal.Action.BUY, symbol, price, 100,
                    current.getDate(), getName(),
                    String.format("Golden Cross: Fast MA(%.2f) crossed above Slow MA(%.2f)", currentFastMA, currentSlowMA),
                    confidence
            );
        }

        // Death Cross: fast MA crosses below slow MA
        if (prevFastMA >= prevSlowMA && currentFastMA < currentSlowMA) {
            double spread = (currentSlowMA - currentFastMA) / currentSlowMA;
            double confidence = Math.min(0.95, 0.6 + spread * 10);
            return new TradingSignal(
                    TradingSignal.Action.SELL, symbol, price, 100,
                    current.getDate(), getName(),
                    String.format("Death Cross: Fast MA(%.2f) crossed below Slow MA(%.2f)", currentFastMA, currentSlowMA),
                    confidence
            );
        }

        return holdSignal(current, symbol,
                String.format("No crossover. Fast MA=%.2f, Slow MA=%.2f", currentFastMA, currentSlowMA));
    }

    private double calculateSMA(List<StockQuote> quotes, int endIndex, int period) {
        double sum = 0;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            sum += quotes.get(i).getClose();
        }
        return sum / period;
    }

    private TradingSignal holdSignal(StockQuote quote, String symbol, String reason) {
        return new TradingSignal(
                TradingSignal.Action.HOLD, symbol, quote.getClose(), 0,
                quote.getDate(), getName(), reason, 0.0
        );
    }
}
