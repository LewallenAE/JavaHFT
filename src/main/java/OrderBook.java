import java.util.*;


/**
 *
 * Centra Limit Order Book: (CLOB) with price-time priority
 *
 * Architecture
 * - Bids stored in descending price order (highest first)
 * - Asks stored in ascending price order (lowest first)
 * - Each price level maintains FIFO queue of orders
 *
 *
 */


public class OrderBook {


    // === DATA STRUCTURES ===

    // Bids: Higher prices first
    // TreeMap keeps pricing sorted, LinkedList maintains FIFO at each price.
    private final TreeMap<Double, LinkedList<Order>> bids;

    // Asks: Lower prices first (natural order)
    private final TreeMap<Double, LinkedList<Order>> asks;

    // Trade History
    private final List<Trade> trades;

    // Order index for O(1) Lookup
    private final Map<Long, OrderLocation> orderIndex;

    // Statics
    private int totalOrdersProcessed;
    private long totalVolumeTraded;

    public OrderBook() {

        // Bids: Reverse order (highest price first)
        this.bids = new TreeMap<>(Collections.reverseOrder());

        // Asks: Natural order (lowest price first)
        this.asks = new TreeMap<>();

        this.trades = new ArrayList<>();
        this.totalOrdersProcessed = 0;
        this.totalVolumeTraded = 0;
        this.orderIndex = new HashMap<>();
    }

    /**
     * Add an order to the book
     * Routes to the correct side, then attempts matching
     */

    public void addOrder(Order order) {
        totalOrdersProcessed++;

        // Set status to active
        order.setStatus(Order.Status.ACTIVE);

        // Add to the appropriate side
        TreeMap<Double, LinkedList<Order>> targetBook =
                (order.getType() == Order.Type.BID) ? bids : asks;

        // Get or create the price level
        LinkedList<Order> priceLevel = targetBook.computeIfAbsent(order.getPrice(), k -> new LinkedList<>());


        // Add to end of queue (FIFO)
        priceLevel.addLast(order);

        // Add to index for fast lookup
        orderIndex.put(
                order.getOrderId(),
                new OrderLocation(order, order.getPrice(), order.getType())
        );

        System.out.printf("Added: %s\n", order);

        // Attempt to match immediately
        matchOrders();
    }

    /**
     *
     * CORE MATCHING ALGORITHM
     *
     */

    public void matchOrders() {

        // Keep matching while both sides have orders
        while (!bids.isEmpty() && !asks.isEmpty()) {

            // Step 1: Get BEST PRICES (not orders yet, just prices)
            Double bestBidPrice = bids.firstKey(); // Highest bid (TreeMap sorted descending)
            Double bestAskPrice = asks.firstKey(); // Lowest Price (TreeMap sorted ascending)

            // Step 2: Check if match is possible
            if (bestBidPrice < bestAskPrice) {
                // Spread too wide - no more matches possible
                // Example: Best bid is $99, best ask is $101 buyers won't pay that much
                break;
            }

            // Step 3: Match is possible! Get teh FIRST ORDER at each price (FIFO)
            LinkedList<Order> bidQueue = bids.get(bestBidPrice);
            LinkedList<Order> askQueue = asks.get(bestAskPrice);

            Order bestBid = bidQueue.getFirst(); // First Order in the queue (FIFO)
            Order bestAsk = askQueue.getFirst(); // First order in the queue (FIFO)

            // Step 4: Calculate trade quantity (might be a partial fill)
            int tradeQuantity = Math.min(
                    bestBid.getRemainingQuantity(),
                    bestAsk.getRemainingQuantity()
            );

            // Step 5: Calculate execution price (midpoint for fairness)
            double tradePrice = (bestBidPrice + bestAskPrice) / 2.0;

            //Step 6: Execute the trade
            executeTrade(bestBid, bestAsk, tradeQuantity, tradePrice);

            // Step 7: Update orders (reduce remaining quantities)
            bestBid.fill(tradeQuantity);
            bestAsk.fill(tradeQuantity);

            // Step 8: Remove fully filled orders from the book
            if(bestBid.isFilled()) {
                bidQueue.removeFirst(); // Remove from FIFO queue

                // Remove from index
                orderIndex.remove(bestBid.getOrderId());

                // If price level is now empty, remove it entirely
                if (bidQueue.isEmpty()) {
                    bids.remove(bestBidPrice);
                }

            }

            if (bestAsk.isFilled()) {
                askQueue.removeFirst();

                // Remove from index
                orderIndex.remove(bestAsk.getOrderId());

                // If price level is now empty, remove it entirely
                if (askQueue.isEmpty()) {
                    asks.remove(bestAskPrice);
                }
            }

            // Loop continues checking for the next possible match
        }
    }

    /**
     * Cancel an order by ID
     *
     * @param
     * @return
     */
    public boolean cancelOrder(long orderId) {
        // Step 1: Look up order location via HashMap
        OrderLocation location = orderIndex.get(orderId);

        // Step2: Check if order exists
        if (location == null) {
            System.out.printf("Cancel failed: order #%d not found.\n", orderId);
            return false;
        }

        Order order = location.getOrder();

        // Step 3: Check if order is still active
        if (order.getStatus() == Order.Status.FILLED ||
            order.getStatus() == Order.Status.CANCELLED) {
            System.out.printf("Cancel failed: Order #%d already %s\n", orderId, order.getStatus());
            return false;
        }

        // Step 4: Get teh appropriate side of the book
        TreeMap<Double, LinkedList<Order>> targetBook = (location.getSide() == Order.Type.BID) ? bids : asks;

        // Step 5: Get the price level queue
        LinkedList<Order> priceLevel = targetBook.get(location.getPriceLevel());

        if (priceLevel == null) {
            System.out.printf("Cancel failed: Price level %.2f not found \n",
                    location.getPriceLevel());
            return false;
        }

        // Step 6: Remove order from the queue
        boolean removed = priceLevel.remove(order);

        if(!removed) {
            System.out.printf("Cancel failed Order #%d not in price level \n", orderId);
            return false;
        }

        // Step 7: If price level is now empty. remove it
        if (priceLevel.isEmpty()) {
            targetBook.remove(location.getPriceLevel());
        }

        // Step8; Update Order Status
        order.setStatus(Order.Status.CANCELLED);

        // Step 9: Remove from index
        orderIndex.remove(orderId);

        System.out.printf("Cancelled: %s\n", order);
        return true;
    }

    /**
     *
     * Get order by ID (without removing it)
     */

    public Order getOrder(long orderId) {
        OrderLocation location = orderIndex.get(orderId);
        return (location != null) ? location.getOrder() : null;
    }

    /**
     * Check if an order exists and if it's active
     */
    public boolean isOrderActive(long orderId) {
        Order order = getOrder(orderId);
        return order != null &&
                (order.getStatus() == Order.Status.ACTIVE ||
                        order.getStatus() == Order.Status.PARTIAL_FILL);
    }

    /**
     * Get all active orders for debugging and display
     */
    public List<Order> getAllActiveOrders() {
        return orderIndex.values().stream()
                .map(OrderLocation::getOrder)
                .filter(o -> o.getStatus() == Order.Status.ACTIVE ||
                        o.getStatus() == Order.Status.PARTIAL_FILL)
                .toList();
    }

    /**
     *
     * Execute a trade and record it
     */

    private void executeTrade(Order bid, Order ask, int quantity, double price) {

        // Create the trade record
        Trade trade = new Trade(
                bid.getOrderId(),
                ask.getOrderId(),
                price,
                quantity
        );

        // Add to trade history
        trades.add(trade);

        // update statistics
        totalVolumeTraded += quantity;

        // Log the trade
        System.out.println(trade);
    }

    /**
     *
     * Get current state of bids
     */

    public Map<Double, Integer> getBidDepth() {
        Map<Double, Integer> depth = new TreeMap<>(Collections.reverseOrder());
        bids.forEach((price, orders) -> {
            int totalQty = orders.stream()
                    .mapToInt(Order::getRemainingQuantity)
                    .sum();
            depth.put(price,totalQty);
        });
        return depth;
    }


    /**
     *
     * Get current state of asks (for display/debugging)
     *
     */
    public Map<Double, Integer> getAskDepth() {
        Map<Double, Integer> depth = new TreeMap<>();
        asks.forEach((price, orders) -> {
            int totalQty = orders.stream()
                    .mapToInt(Order::getRemainingQuantity)
                    .sum();
            depth.put(price, totalQty);
        });
        return depth;
    }

    /**
     *
     * Get all executed trades
     */

    public List<Trade> getTrades() {
        return new ArrayList<>(trades);
    }


    /**
     *
     * Get spread (difference between best bid and best ask)
     * Returns null if one side is empty
     */
    public Double getSpread() {
        if (bids.isEmpty() || asks.isEmpty()) {
            return null;
        }
        return asks.firstKey() - bids.firstKey();
    }


    /**
     *
     * Get mid-market price (average of best bid and best ask)
     */
    public Double getMidPrice() {
        if (bids.isEmpty() || asks.isEmpty()) {
            return null;
        }
        return (bids.firstKey() + asks.firstKey()) / 2.0;
    }
    /**
     *
     * Display current order book state
     */

    public void printBook() {
        System.out.println("\n=== ORDER BOOK ===");

        System.out.println("ASKS (Sellers):");
        getAskDepth().forEach((price, qty) ->
                System.out.printf(" $%.2f -> %d shares\n", price, qty)
        );

        Double spread = getSpread();
        if (spread != null) {
            System.out.printf("--- SPREAD: $%.2f ---\n", spread);
        } else {
            System.out.println("--- NO SPREAD (one side is empty) ---");
        }

        System.out.println("BIDS (Buyers):");
        getBidDepth().forEach((price,qty) ->
                System.out.printf("  $%.2f -> %d shares\n", price, qty)
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

} // End OrderBook.java class