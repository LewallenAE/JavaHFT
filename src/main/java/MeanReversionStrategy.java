import java.util.List;

/**
 * Mean Reversion Strategy using Bollinger Bands.
 *
 * Core idea: prices tend to revert to the mean. When price deviates
 * significantly from its moving average (measured by standard deviations),
 * it's likely to snap back.
 *
 * - BUY when price drops below the lower Bollinger Band (oversold)
 * - SELL when price rises above the upper Bollinger Band (overbought)
 *
 * Parameters:
 * - Period: lookback window for SMA and standard deviation (default 20)
 * - Num StdDevs: band width in standard deviations (default 2.0)
 *
 * @author Anthony Lewallen
 */
public class MeanReversionStrategy implements TradingStrategy {

    private final int period;
    private final double numStdDevs;

    public MeanReversionStrategy() {
        this(20, 2.0);
    }

    public MeanReversionStrategy(int period, double numStdDevs) {
        this.period = period;
        this.numStdDevs = numStdDevs;
    }

    @Override
    public String getName() {
        return String.format("Mean Reversion (BB %d/%.1f)", period, numStdDevs);
    }

    @Override
    public TradingSignal evaluate(List<StockQuote> quotes, int currentIndex, String symbol) {
        if (currentIndex < period) {
            return holdSignal(quotes.get(currentIndex), symbol, "Insufficient data for Bollinger Bands");
        }

        // Calculate SMA
        double sma = 0;
        for (int i = currentIndex - period + 1; i <= currentIndex; i++) {
            sma += quotes.get(i).getClose();
        }
        sma /= period;

        // Calculate standard deviation
        double variance = 0;
        for (int i = currentIndex - period + 1; i <= currentIndex; i++) {
            double diff = quotes.get(i).getClose() - sma;
            variance += diff * diff;
        }
        double stdDev = Math.sqrt(variance / period);

        // Bollinger Bands
        double upperBand = sma + (numStdDevs * stdDev);
        double lowerBand = sma - (numStdDevs * stdDev);

        StockQuote current = quotes.get(currentIndex);
        double price = current.getClose();

        // Calculate z-score (how many standard deviations from the mean)
        double zScore = (price - sma) / stdDev;

        // BUY signal: price below lower band (oversold)
        if (price <= lowerBand) {
            double confidence = Math.min(0.95, 0.5 + Math.abs(zScore) * 0.15);
            return new TradingSignal(
                    TradingSignal.Action.BUY, symbol, price, 100,
                    current.getDate(), getName(),
                    String.format("Oversold: Price $%.2f below lower band $%.2f (z=%.2f)",
                            price, lowerBand, zScore),
                    confidence
            );
        }

        // SELL signal: price above upper band (overbought)
        if (price >= upperBand) {
            double confidence = Math.min(0.95, 0.5 + Math.abs(zScore) * 0.15);
            return new TradingSignal(
                    TradingSignal.Action.SELL, symbol, price, 100,
                    current.getDate(), getName(),
                    String.format("Overbought: Price $%.2f above upper band $%.2f (z=%.2f)",
                            price, upperBand, zScore),
                    confidence
            );
        }

        return holdSignal(current, symbol,
                String.format("Price within bands [%.2f - %.2f], z-score=%.2f", lowerBand, upperBand, zScore));
    }

    private TradingSignal holdSignal(StockQuote quote, String symbol, String reason) {
        return new TradingSignal(
                TradingSignal.Action.HOLD, symbol, quote.getClose(), 0,
                quote.getDate(), getName(), reason, 0.0
        );
    }
}
