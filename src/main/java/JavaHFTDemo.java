public class JavaHFTDemo {

    public static void main(String[] args) {

        OrderBook book = new OrderBook();

        System.out.println("=== JavaHFT Matching Engine Demo ===\n");

        // Add some orders
        System.out.println("Adding initial orders...\n");

        // Bids (Buyers)
        book.addOrder(new Order(Order.Type.BID, 150.25, 100));
        book.addOrder(new Order(Order.Type.BID, 150.25, 50));
        book.addOrder(new Order(Order.Type.BID, 149.75, 200));

        Order bid1 = new Order(Order.Type.BID, 160.50, 100);
        Order bid2 = new Order(Order.Type.BID, 155.79, 25);
        Order bid3 = new Order(Order.Type.BID, 158.88, 350);

        book.addOrder(bid1);
        book.addOrder(bid2);
        book.addOrder(bid3);

        Order ask1 = new Order(Order.Type.ASK, 159.99, 75);
        Order ask2 = new Order(Order.Type.ASK, 150.50, 75);
        Order ask3 = new Order(Order.Type.ASK, 150.50, 100);

        book.addOrder(ask1);
        book.addOrder(ask2);
        book.addOrder(ask3);

        book.printBook();

        // Test Cancellation
        System.out.println("\n=== TESTING CANCELLATION ===\n");

        // Cancel bid 2
        System.out.println("Attempting to cancel Order #" + bid2.getOrderId());
        boolean cancelled = book.cancelOrder(bid2.getOrderId());
        System.out.println("Result: " + (cancelled ? "SUCCESS" : "FAILED") + "\n");

        // Show book state (no matches yet - spread too wide)
        book.printBook();

        // Try the same one again to make sure it's working correctly
        System.out.println("Attempting to cancel Order #" + bid2.getOrderId() + " again");
        cancelled = book.cancelOrder(bid2.getOrderId());
        System.out.println("Result: " + (cancelled ? "SUCCESS" : "FAILED") + "\n");

        // Cancel non-existent order should print fail
        System.out.println("--- Try to cencel non-existing order ---\n");
        System.out.println("Attempting to cancel order #99999");
        cancelled = book.cancelOrder(99999);
        System.out.println("Result: " + (cancelled ? "SUCCESS" : "FAILED") + "\n");

        // Asks (Sellers)
        book.addOrder(new Order(Order.Type.ASK, 150.00, 75));
        book.addOrder(new Order(Order.Type.ASK, 150.00, 100));
        book.addOrder(new Order(Order.Type.ASK, 152.00, 50));

        // Add an aggressive seller that crosses the spread
        System.out.println("\n\n=== Adding aggressive seller at $150.00 ===\n");
        book.addOrder(new Order(Order.Type.ASK, 150.00, 150));

        // Show final book state
        book.printBook();

        // Try to cancel a filled order
        System.out.println("\n--- Try to cancel a filled order ---\n");
        System.out.println("Attempting to cancel filled Order #" + bid1.getOrderId());
        cancelled = book.cancelOrder(bid1.getOrderId());
        System.out.println("Result: " + (cancelled? "SUCCESS" : "FAILED") + "\n");

        System.out.println("\n=== Demo Complete ===");
        System.out.println("Built by Anthony E. Lewallen");
        System.out.println("Github: https://github.com/LewallenAE/JavaHFT");

    } // end of the main method
} // end Demo class
