import java.util.List;

/**
 * Interface for all trading strategies.
 * Strategies analyze historical price data and generate trading signals.
 *
 * @author Anthony Lewallen
 */
public interface TradingStrategy {

    /** Name of this strategy */
    String getName();

    /**
     * Analyze historical quotes and generate a signal for the current bar.
     *
     * @param quotes historical price data (oldest first)
     * @param currentIndex the index of the current bar to evaluate
     * @param symbol the ticker symbol
     * @return a TradingSignal (BUY, SELL, or HOLD)
     */
    TradingSignal evaluate(List<StockQuote> quotes, int currentIndex, String symbol);
}
