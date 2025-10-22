/**
 *
 *      This is an immutable record of an executed trade
 *      This is generated when a bid and an ask match
 * @Author Anthony Lewallen
 *
 *
 *
 *
 */

public class Trade {

    private final long tradeId;
    private final long buyOrderId;  // Bid order
    private final long sellOrderId; // Sell order / ask order
    private final double price;     // Execution price
    private final int quantity;    // Number of shares traded
    private final long timeStamp;  // When trade occurred

    private static long tradeCounter = 0;

    public Trade(long buyOrderId, long sellOrderId, double price, int quantity) {
        this.tradeId = ++tradeCounter;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.quantity = quantity;
        this.timeStamp = System.nanoTime();
    }

    // Getters

    public long getTradeId(){return tradeId;}
    public long getBuyOrderId(){return buyOrderId;}
    public long getSellOrderId(){return sellOrderId;}
    public double getPrice(){return price;}
    public int getQuantity(){return quantity;}
    public long getTimeStamp(){return timeStamp;}

    @Override
    public String toString() {
        return String.format("TRADE[id=%d, buyOrder=%d, sellOrder=%d, %d @ $%.2f]",
            tradeId, buyOrderId, sellOrderId, quantity, price);
    } // End String Override

} // End Trade Class
