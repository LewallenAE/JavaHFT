import java.time.LocalDate;

/**
 * OHLCV stock quote data point.
 * Represents a single candlestick of market data.
 *
 * @author Anthony Lewallen
 */
public class StockQuote {

    private final LocalDate date;
    private final double open;
    private final double high;
    private final double low;
    private final double close;
    private final long volume;

    public StockQuote(LocalDate date, double open, double high, double low, double close, long volume) {
        this.date = date;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public LocalDate getDate() { return date; }
    public double getOpen() { return open; }
    public double getHigh() { return high; }
    public double getLow() { return low; }
    public double getClose() { return close; }
    public long getVolume() { return volume; }

    /** Typical price used in VWAP calculations */
    public double getTypicalPrice() {
        return (high + low + close) / 3.0;
    }

    @Override
    public String toString() {
        return String.format("StockQuote[%s O=%.2f H=%.2f L=%.2f C=%.2f V=%d]",
                date, open, high, low, close, volume);
    }
}
