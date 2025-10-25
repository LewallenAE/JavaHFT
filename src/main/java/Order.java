import java.util.concurrent.atomic.AtomicLong;

public class Order implements Comparable<Order>{


    private static final AtomicLong ID_Generator = new AtomicLong(0);

    public enum Type{ BID, ASK}

    public enum Status {
        NEW,                // Just created not in book yet
        ACTIVE,             // In the order book, waiting to match
        PARTIAL_FILL,       // Some quantity filled, still active
        FILLED,             // Completely filled
        CANCELLED           // User cancelled
    }
    private final Type type;
    private final double price;
    private final int quantity;
    private long orderID;
    private long timeStamp;
    private int remainingQuantity;
    private Status status;

    public Order(Type type, double price, int quantity) {
        this.orderID = ID_Generator.incrementAndGet();
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.timeStamp = System.nanoTime();
        this.status = Status.NEW;
    }

    public long getOrderId() { return orderID;}
    public Type getType() {return type;}
    public double getPrice() { return price; }
    public int getQuantity() {return quantity;}
    public int getRemainingQuantity() { return remainingQuantity;}
    public long getTimeStamp() { return timeStamp; }
    public Status getStatus(){return status;}

    public void setStatus(Status status) {
        this.status = status;
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
        if(this.type == Type.BID) {
            priceComparison = Double.compare(other.price, this.price);
        } else {
            priceComparison = Double.compare(this.price, other.price);
        }

        if (priceComparison != 0) {
            return priceComparison;
        }

        return Long.compare(this.timeStamp, other.timeStamp);
    }

    @Override
    public String toString() {
        return String.format("Order[id=%d, %s, $%.2f, qty=%d/%d, status=%s, ts=%d]",
                orderID, type, price, remainingQuantity, quantity, status, timeStamp);
    }

}


