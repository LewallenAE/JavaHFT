import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks portfolio positions, P&L, and performance metrics.
 * Used by the backtesting engine to evaluate strategy performance.
 *
 * @author Anthony Lewallen
 */
public class PortfolioTracker {

    private double cash;
    private int position;         // current shares held (positive = long)
    private double avgEntryPrice;
    private double realizedPnL;
    private double peakValue;
    private double maxDrawdown;
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;

    private final List<EquityPoint> equityCurve;
    private final List<TradeRecord> tradeHistory;

    public PortfolioTracker(double initialCash) {
        this.cash = initialCash;
        this.position = 0;
        this.avgEntryPrice = 0;
        this.realizedPnL = 0;
        this.peakValue = initialCash;
        this.maxDrawdown = 0;
        this.totalTrades = 0;
        this.winningTrades = 0;
        this.losingTrades = 0;
        this.equityCurve = new ArrayList<>();
        this.tradeHistory = new ArrayList<>();

        equityCurve.add(new EquityPoint(null, initialCash));
    }

    /** Execute a buy */
    public void buy(int quantity, double price, LocalDate date) {
        double cost = quantity * price;
        if (cost > cash) {
            quantity = (int) (cash / price);
            if (quantity <= 0) return;
            cost = quantity * price;
        }

        // Update average entry price
        if (position > 0) {
            avgEntryPrice = ((avgEntryPrice * position) + cost) / (position + quantity);
        } else {
            avgEntryPrice = price;
        }

        cash -= cost;
        position += quantity;
        totalTrades++;

        tradeHistory.add(new TradeRecord("BUY", quantity, price, date));
    }

    /** Execute a sell */
    public void sell(int quantity, double price, LocalDate date) {
        if (position <= 0) return;
        quantity = Math.min(quantity, position);

        double proceeds = quantity * price;
        double pnl = (price - avgEntryPrice) * quantity;

        cash += proceeds;
        position -= quantity;
        realizedPnL += pnl;
        totalTrades++;

        if (pnl > 0) winningTrades++;
        else if (pnl < 0) losingTrades++;

        if (position == 0) avgEntryPrice = 0;

        tradeHistory.add(new TradeRecord("SELL", quantity, price, date));
    }

    /** Record equity value for charting */
    public void recordEquity(double currentPrice, LocalDate date) {
        double totalValue = cash + (position * currentPrice);
        equityCurve.add(new EquityPoint(date, totalValue));

        if (totalValue > peakValue) {
            peakValue = totalValue;
        }
        double drawdown = (peakValue - totalValue) / peakValue;
        if (drawdown > maxDrawdown) {
            maxDrawdown = drawdown;
        }
    }

    /** Get total portfolio value at given price */
    public double getTotalValue(double currentPrice) {
        return cash + (position * currentPrice);
    }

    /** Get unrealized P&L */
    public double getUnrealizedPnL(double currentPrice) {
        if (position == 0) return 0;
        return (currentPrice - avgEntryPrice) * position;
    }

    // Getters
    public double getCash() { return cash; }
    public int getPosition() { return position; }
    public double getAvgEntryPrice() { return avgEntryPrice; }
    public double getRealizedPnL() { return realizedPnL; }
    public double getMaxDrawdown() { return maxDrawdown; }
    public int getTotalTrades() { return totalTrades; }
    public int getWinningTrades() { return winningTrades; }
    public int getLosingTrades() { return losingTrades; }
    public List<EquityPoint> getEquityCurve() { return equityCurve; }
    public List<TradeRecord> getTradeHistory() { return tradeHistory; }

    public double getWinRate() {
        if (totalTrades == 0) return 0;
        return (double) winningTrades / totalTrades;
    }

    /** Print portfolio summary */
    public void printSummary(double currentPrice) {
        double totalValue = getTotalValue(currentPrice);
        double initialValue = equityCurve.get(0).value;
        double totalReturn = (totalValue - initialValue) / initialValue * 100;

        System.out.println("\n=== PORTFOLIO SUMMARY ===");
        System.out.printf("Cash:              $%,.2f\n", cash);
        System.out.printf("Position:          %d shares @ $%.2f avg\n", position, avgEntryPrice);
        System.out.printf("Total Value:       $%,.2f\n", totalValue);
        System.out.printf("Total Return:      %.2f%%\n", totalReturn);
        System.out.printf("Realized P&L:      $%,.2f\n", realizedPnL);
        System.out.printf("Unrealized P&L:    $%,.2f\n", getUnrealizedPnL(currentPrice));
        System.out.printf("Max Drawdown:      %.2f%%\n", maxDrawdown * 100);
        System.out.printf("Total Trades:      %d\n", totalTrades);
        System.out.printf("Win Rate:          %.1f%% (%d W / %d L)\n",
                getWinRate() * 100, winningTrades, losingTrades);
    }

    /** Data point for equity curve charting */
    public static class EquityPoint {
        public final LocalDate date;
        public final double value;

        public EquityPoint(LocalDate date, double value) {
            this.date = date;
            this.value = value;
        }
    }

    /** Record of an executed trade */
    public static class TradeRecord {
        public final String side;
        public final int quantity;
        public final double price;
        public final LocalDate date;

        public TradeRecord(String side, int quantity, double price, LocalDate date) {
            this.side = side;
            this.quantity = quantity;
            this.price = price;
            this.date = date;
        }

        @Override
        public String toString() {
            return String.format("%s %d @ $%.2f on %s", side, quantity, price, date);
        }
    }
}
