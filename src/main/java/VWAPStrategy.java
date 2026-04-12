import java.util.List;

/**
 * Volume-Weighted Average Price (VWAP) Strategy.
 *
 * VWAP is the benchmark institutional traders use. It represents the
 * average price weighted by volume - the "fair value" of a stock for the day.
 *
 * - BUY when price drops significantly below VWAP (discount to fair value)
 * - SELL when price rises significantly above VWAP (premium to fair value)
 *
 * This is a core HFT/quant strategy used by execution desks worldwide.
 *
 * @author Anthony Lewallen
 */
public class VWAPStrategy implements TradingStrategy {

    private final int lookbackPeriod;
    private final double threshold;  // % deviation from VWAP to trigger signal

    public VWAPStrategy() {
        this(20, 0.02);  // 20-day VWAP, 2% threshold
    }

    public VWAPStrategy(int lookbackPeriod, double threshold) {
        this.lookbackPeriod = lookbackPeriod;
        this.threshold = threshold;
    }

    @Override
    public String getName() {
        return String.format("VWAP (%d-day, %.1f%%)", lookbackPeriod, threshold * 100);
    }

    @Override
    public TradingSignal evaluate(List<StockQuote> quotes, int currentIndex, String symbol) {
        if (currentIndex < lookbackPeriod) {
            return holdSignal(quotes.get(currentIndex), symbol, "Insufficient data for VWAP");
        }

        // Calculate VWAP over lookback period
        double cumulativeTPV = 0;  // cumulative (typical price * volume)
        long cumulativeVolume = 0;

        for (int i = currentIndex - lookbackPeriod + 1; i <= currentIndex; i++) {
            StockQuote q = quotes.get(i);
            double typicalPrice = q.getTypicalPrice();
            cumulativeTPV += typicalPrice * q.getVolume();
            cumulativeVolume += q.getVolume();
        }

        if (cumulativeVolume == 0) {
            return holdSignal(quotes.get(currentIndex), symbol, "No volume data");
        }

        double vwap = cumulativeTPV / cumulativeVolume;
        StockQuote current = quotes.get(currentIndex);
        double price = current.getClose();
        double deviation = (price - vwap) / vwap;

        // BUY: price significantly below VWAP (trading at discount)
        if (deviation < -threshold) {
            double confidence = Math.min(0.95, 0.5 + Math.abs(deviation) * 5);
            return new TradingSignal(
                    TradingSignal.Action.BUY, symbol, price, 100,
                    current.getDate(), getName(),
                    String.format("Below VWAP: Price $%.2f vs VWAP $%.2f (%.2f%% discount)",
                            price, vwap, deviation * 100),
                    confidence
            );
        }

        // SELL: price significantly above VWAP (trading at premium)
        if (deviation > threshold) {
            double confidence = Math.min(0.95, 0.5 + Math.abs(deviation) * 5);
            return new TradingSignal(
                    TradingSignal.Action.SELL, symbol, price, 100,
                    current.getDate(), getName(),
                    String.format("Above VWAP: Price $%.2f vs VWAP $%.2f (%.2f%% premium)",
                            price, vwap, deviation * 100),
                    confidence
            );
        }

        return holdSignal(current, symbol,
                String.format("Near VWAP: Price $%.2f vs VWAP $%.2f (%.2f%%)", price, vwap, deviation * 100));
    }

    private TradingSignal holdSignal(StockQuote quote, String symbol, String reason) {
        return new TradingSignal(
                TradingSignal.Action.HOLD, symbol, quote.getClose(), 0,
                quote.getDate(), getName(), reason, 0.0
        );
    }
}
