import java.util.concurrent.atomic.AtomicLong;

public class Order implements Comparable<Order> {

    // $0.01 per tick -- all prices stored as integer ticks to avoid floating-point key bugs
    static final int TICK_SIZE = 100;

    private static final AtomicLong ID_Generator = new AtomicLong(0);

    public enum Type { BID, ASK }

    public enum Status {
        NEW,
        ACTIVE,
        PARTIAL_FILL,
        FILLED,
        CANCELLED,
        REJECTED
    }

    private final Type type;
    private final long priceTicks;   // price in integer ticks (dollars x TICK_SIZE)
    private final int quantity;
    private long orderID;
    private long timeStamp;
    private int remainingQuantity;
    private Status status;
    private final boolean isMarketOrder;

    public Order(Type type, double price, int quantity) {
        this.orderID = ID_Generator.incrementAndGet();
        this.type = type;
        this.priceTicks = Math.round(price * TICK_SIZE);
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.timeStamp = System.nanoTime();
        this.status = Status.NEW;
        this.isMarketOrder = false;
    }

    private Order(Type type, int quantity, boolean isMarketOrder) {
        this.orderID = ID_Generator.incrementAndGet();
        this.type = type;
        this.priceTicks = 0;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.timeStamp = System.nanoTime();
        this.status = Status.NEW;
        this.isMarketOrder = isMarketOrder;
    }

    public long getOrderId() { return orderID; }
    public Type getType() { return type; }
    /** Returns price in dollars (for display and trade recording). */
    public double getPrice() { return priceTicks / (double) TICK_SIZE; }
    /** Returns price as integer ticks -- use this as TreeMap key. */
    public long getPriceTicks() { return priceTicks; }
    public int getQuantity() { return quantity; }
    public int getRemainingQuantity() { return remainingQuantity; }
    public long getTimeStamp() { return timeStamp; }
    public Status getStatus() { return status; }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isMarketOrder() {
        return isMarketOrder;
    }

    public static Order createMarketOrder(Type type, int quantity) {
        return new Order(type, quantity, true);
    }

    public int fill(int fillQuantity) {
        int actualFill = Math.min(fillQuantity, remainingQuantity);
        remainingQuantity -= actualFill;

        if (remainingQuantity == 0) {
            this.status = Status.FILLED;
        } else if (remainingQuantity < quantity) {
            this.status = Status.PARTIAL_FILL;
        }
        return actualFill;
    }

    public boolean isFilled() {
        return remainingQuantity == 0;
    }

    @Override
    public int compareTo(Order other) {
        if (this.type != other.type) {
            throw new IllegalArgumentException("Cannot compare BID to ASK");
        }

        int priceComparison;
        if (this.type == Type.BID) {
            priceComparison = Long.compare(other.priceTicks, this.priceTicks);
        } else {
            priceComparison = Long.compare(this.priceTicks, other.priceTicks);
        }

        if (priceComparison != 0) {
            return priceComparison;
        }

        return Long.compare(this.timeStamp, other.timeStamp);
    }

    @Override
    public String toString() {
        if (isMarketOrder) {
            return String.format("Order[id=%d, %s, MARKET, qty=%d/%d, status=%s, ts=%d]",
                    orderID, type, remainingQuantity, quantity, status, timeStamp);
        } else {
            return String.format("Order[id=%d, %s, $%.2f, qty=%d/%d, status=%s, ts=%d]",
                    orderID, type, getPrice(), remainingQuantity, quantity, status, timeStamp);
        }
    }
}
