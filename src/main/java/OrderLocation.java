/**
 * Tracks the location of an order in the order book.
 * Enables O(1) order lookup for cancellation.
 */
public class OrderLocation {

    private final Order order;
    private final long priceLevel;   // price in ticks (matches TreeMap<Long,...> key)
    private final Order.Type side;

    public OrderLocation(Order order, long priceLevel, Order.Type side) {
        this.order = order;
        this.priceLevel = priceLevel;
        this.side = side;
    }

    public Order getOrder() {
        return order;
    }

    public long getPriceLevel() {
        return priceLevel;
    }

    public Order.Type getSide() {
        return side;
    }

    @Override
    public String toString() {
        return String.format("OrderLocation[order=%d, price=%.2f, side=%s]",
                order.getOrderId(), priceLevel / (double) Order.TICK_SIZE, side);
    }
}
