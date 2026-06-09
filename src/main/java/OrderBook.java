import java.util.*;


/**
 * Central Limit Order Book (CLOB) with price-time priority.
 *
 * Architecture:
 * - Bids stored in descending price order (highest first)
 * - Asks stored in ascending price order (lowest first)
 * - Each price level maintains a FIFO queue of orders
 * - Prices keyed by long ticks (Order.TICK_SIZE per dollar) to ensure
 *   exact integer equality and avoid floating-point TreeMap key bugs
 */
public class OrderBook {

    // Bids: higher ticks first (TreeMap reversed)
    private final TreeMap<Long, LinkedList<Order>> bids;

    // Asks: lower ticks first (natural order)
    private final TreeMap<Long, LinkedList<Order>> asks;

    private final List<Trade> trades;

    // Order index for O(1) lookup and cancellation
    private final Map<Long, OrderLocation> orderIndex;

    private int totalOrdersProcessed;
    private long totalVolumeTraded;

    public OrderBook() {
        this.bids = new TreeMap<>(Collections.reverseOrder());
        this.asks = new TreeMap<>();
        this.trades = new ArrayList<>();
        this.totalOrdersProcessed = 0;
        this.totalVolumeTraded = 0;
        this.orderIndex = new HashMap<>();
    }

    /**
     * Add an order to the book.
     * Routes to the correct side, then attempts matching.
     */
    public void addOrder(Order order) {
        if (order.isMarketOrder()) {
            executeMarketOrder(order);
            return;
        }

        totalOrdersProcessed++;
        order.setStatus(Order.Status.ACTIVE);

        TreeMap<Long, LinkedList<Order>> targetBook =
                (order.getType() == Order.Type.BID) ? bids : asks;

        long key = order.getPriceTicks();
        LinkedList<Order> priceLevel = targetBook.computeIfAbsent(key, k -> new LinkedList<>());
        priceLevel.addLast(order);

        orderIndex.put(
                order.getOrderId(),
                new OrderLocation(order, key, order.getType())
        );

        System.out.printf("Added: %s\n", order);
        matchOrders();
    }

    /**
     * Core matching algorithm -- price-time priority.
     */
    public void matchOrders() {
        while (!bids.isEmpty() && !asks.isEmpty()) {

            Long bestBidTicks = bids.firstKey();
            Long bestAskTicks = asks.firstKey();

            if (bestBidTicks < bestAskTicks) {
                break;
            }

            LinkedList<Order> bidQueue = bids.get(bestBidTicks);
            LinkedList<Order> askQueue = asks.get(bestAskTicks);

            Order bestBid = bidQueue.getFirst();
            Order bestAsk = askQueue.getFirst();

            int tradeQuantity = Math.min(
                    bestBid.getRemainingQuantity(),
                    bestAsk.getRemainingQuantity()
            );

            // Midpoint price in dollars
            double tradePrice = (bestBidTicks + bestAskTicks) / 2.0 / Order.TICK_SIZE;

            executeTrade(bestBid, bestAsk, tradeQuantity, tradePrice);

            bestBid.fill(tradeQuantity);
            bestAsk.fill(tradeQuantity);

            if (bestBid.isFilled()) {
                bidQueue.removeFirst();
                orderIndex.remove(bestBid.getOrderId());
                if (bidQueue.isEmpty()) {
                    bids.remove(bestBidTicks);
                }
            }

            if (bestAsk.isFilled()) {
                askQueue.removeFirst();
                orderIndex.remove(bestAsk.getOrderId());
                if (askQueue.isEmpty()) {
                    asks.remove(bestAskTicks);
                }
            }
        }
    }

    /**
     * Cancel an order by ID.
     */
    public boolean cancelOrder(long orderId) {
        OrderLocation location = orderIndex.get(orderId);

        if (location == null) {
            System.out.printf("Cancel failed: order #%d not found.\n", orderId);
            return false;
        }

        Order order = location.getOrder();

        if (order.getStatus() == Order.Status.FILLED ||
            order.getStatus() == Order.Status.CANCELLED) {
            System.out.printf("Cancel failed: Order #%d already %s\n", orderId, order.getStatus());
            return false;
        }

        TreeMap<Long, LinkedList<Order>> targetBook =
                (location.getSide() == Order.Type.BID) ? bids : asks;

        LinkedList<Order> priceLevel = targetBook.get(location.getPriceLevel());

        if (priceLevel == null) {
            System.out.printf("Cancel failed: Price level $%.2f not found\n",
                    location.getPriceLevel() / (double) Order.TICK_SIZE);
            return false;
        }

        boolean removed = priceLevel.remove(order);

        if (!removed) {
            System.out.printf("Cancel failed: Order #%d not in price level\n", orderId);
            return false;
        }

        if (priceLevel.isEmpty()) {
            targetBook.remove(location.getPriceLevel());
        }

        order.setStatus(Order.Status.CANCELLED);
        orderIndex.remove(orderId);

        System.out.printf("Cancelled: %s\n", order);
        return true;
    }

    public Order getOrder(long orderId) {
        OrderLocation location = orderIndex.get(orderId);
        return (location != null) ? location.getOrder() : null;
    }

    public boolean isOrderActive(long orderId) {
        Order order = getOrder(orderId);
        return order != null &&
                (order.getStatus() == Order.Status.ACTIVE ||
                        order.getStatus() == Order.Status.PARTIAL_FILL);
    }

    public List<Order> getAllActiveOrders() {
        return orderIndex.values().stream()
                .map(OrderLocation::getOrder)
                .filter(o -> o.getStatus() == Order.Status.ACTIVE ||
                        o.getStatus() == Order.Status.PARTIAL_FILL)
                .toList();
    }

    private void executeTrade(Order bid, Order ask, int quantity, double price) {
        Trade trade = new Trade(bid.getOrderId(), ask.getOrderId(), price, quantity);
        trades.add(trade);
        totalVolumeTraded += quantity;
        System.out.println(trade);
    }

    /** Bid depth keyed by price ticks. Divide by Order.TICK_SIZE for dollar display. */
    public Map<Long, Integer> getBidDepth() {
        Map<Long, Integer> depth = new TreeMap<>(Collections.reverseOrder());
        bids.forEach((ticks, orders) ->
            depth.put(ticks, orders.stream().mapToInt(Order::getRemainingQuantity).sum())
        );
        return depth;
    }

    /** Ask depth keyed by price ticks. Divide by Order.TICK_SIZE for dollar display. */
    public Map<Long, Integer> getAskDepth() {
        Map<Long, Integer> depth = new TreeMap<>();
        asks.forEach((ticks, orders) ->
            depth.put(ticks, orders.stream().mapToInt(Order::getRemainingQuantity).sum())
        );
        return depth;
    }

    public List<Trade> getTrades() {
        return new ArrayList<>(trades);
    }

    /** Returns spread in dollars, or null if one side is empty. */
    public Double getSpread() {
        if (bids.isEmpty() || asks.isEmpty()) {
            return null;
        }
        return (asks.firstKey() - bids.firstKey()) / (double) Order.TICK_SIZE;
    }

    /** Returns mid-market price in dollars, or null if one side is empty. */
    public Double getMidPrice() {
        if (bids.isEmpty() || asks.isEmpty()) {
            return null;
        }
        return (bids.firstKey() + asks.firstKey()) / 2.0 / Order.TICK_SIZE;
    }

    /**
     * Execute a market order immediately by walking through price levels.
     */
    public List<Trade> executeMarketOrder(Order marketOrder) {
        if (!marketOrder.isMarketOrder()) {
            throw new IllegalArgumentException("Order is not a market order");
        }

        totalOrdersProcessed++;
        marketOrder.setStatus(Order.Status.ACTIVE);

        List<Trade> marketTrades = new ArrayList<>();

        System.out.printf("Executing market order: %s\n", marketOrder);

        TreeMap<Long, LinkedList<Order>> targetBook =
                (marketOrder.getType() == Order.Type.BID) ? asks : bids;

        while (!marketOrder.isFilled() && !targetBook.isEmpty()) {

            Long bestTicks = targetBook.firstKey();
            LinkedList<Order> priceLevel = targetBook.get(bestTicks);
            Order counterOrder = priceLevel.getFirst();

            int tradeQuantity = Math.min(
                    marketOrder.getRemainingQuantity(),
                    counterOrder.getRemainingQuantity()
            );

            double tradePrice = bestTicks / (double) Order.TICK_SIZE;

            Trade trade;
            if (marketOrder.getType() == Order.Type.BID) {
                trade = new Trade(marketOrder.getOrderId(), counterOrder.getOrderId(),
                        tradePrice, tradeQuantity);
            } else {
                trade = new Trade(counterOrder.getOrderId(), marketOrder.getOrderId(),
                        tradePrice, tradeQuantity);
            }

            trades.add(trade);
            marketTrades.add(trade);
            totalVolumeTraded += tradeQuantity;

            System.out.println(trade);

            marketOrder.fill(tradeQuantity);
            counterOrder.fill(tradeQuantity);

            if (counterOrder.isFilled()) {
                priceLevel.removeFirst();
                orderIndex.remove(counterOrder.getOrderId());
                if (priceLevel.isEmpty()) {
                    targetBook.remove(bestTicks);
                }
            }
        }

        if (!marketOrder.isFilled()) {
            int filledQuantity = marketOrder.getQuantity() - marketOrder.getRemainingQuantity();
            if (filledQuantity == 0) {
                marketOrder.setStatus(Order.Status.REJECTED);
                System.out.printf("x REJECTED: Market order #%d - no liquidity available (0/%d shares)\n",
                        marketOrder.getOrderId(), marketOrder.getQuantity());
            } else {
                marketOrder.setStatus(Order.Status.PARTIAL_FILL);
                System.out.printf("Warning: Market order #%d partially filled (%d/%d shares)\n",
                        marketOrder.getOrderId(), filledQuantity, marketOrder.getQuantity());
            }
        }

        return marketTrades;
    }

    public void printBook() {
        System.out.println("\n=== ORDER BOOK ===");

        System.out.println("ASKS (Sellers):");
        getAskDepth().forEach((ticks, qty) ->
                System.out.printf("  $%.2f -> %d shares\n", ticks / (double) Order.TICK_SIZE, qty)
        );

        Double spread = getSpread();
        if (spread != null) {
            System.out.printf("--- SPREAD: $%.2f ---\n", spread);
        } else {
            System.out.println("--- NO SPREAD (one side is empty) ---");
        }

        System.out.println("BIDS (Buyers):");
        getBidDepth().forEach((ticks, qty) ->
                System.out.printf("  $%.2f -> %d shares\n", ticks / (double) Order.TICK_SIZE, qty)
        );

        System.out.println("\n=== STATISTICS ===");
        System.out.printf("Total Orders: %d\n", totalOrdersProcessed);
        System.out.printf("Total Trades: %d\n", trades.size());
        System.out.printf("Total Volume: %d shares\n", totalVolumeTraded);

        Double midPrice = getMidPrice();
        if (midPrice != null) {
            System.out.printf("Mid Price: $%.2f\n", midPrice);
        }
    }
}
