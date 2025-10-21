import java.util.concurrent.*;
import java.util.*;

public class OrderBook {

    private final ConcurrentMap<Double, Integer> bids = new ConcurrentHashMap<>();
    private final ConcurrentMap<Double, Integer> asks = new ConcurrentHashMap<>();

    public void addOrder(Order order) {
        ConcurrentMap<Double, Integer> book =
                (order.getType() == Order.Type.BID) ? bids : asks;
        book.merge(order.getPrice(), order.getQuantity(), Integer::sum);
    }

    public void matchOrders() {
        bids.forEach((bidPrice, bidQty) -> {
            asks.forEach((askPrice, askQty) -> {
                if (bidPrice >= askPrice) {
                    int tradeQty = Math.min(bidQty, askQty);
                    double midpoint = (bidPrice + askPrice) / 2;
                    logTrade(tradeQty, midpoint);
                    updateBooks(bidPrice, askPrice, tradeQty);
                }
            });
        });
    }

    private void logTrade(int qty, double price) {
        System.out.printf("TRADE: %d @ $%.2f\n", qty, price);
    }

    private void updateBooks(double bidPrice, double askPrice, int tradeQty) {
        bids.computeIfPresent(bidPrice, (k, v) -> v - tradeQty);
        asks.computeIfPresent(askPrice, (k, v) -> v - tradeQty);
    }
}
