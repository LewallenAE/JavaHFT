public class JavaHFTDemo {

    public static void main(String[] args) {

        OrderBook book = new OrderBook();


        // Bids (Buyers)
        book.addOrder(new Order(Order.Type.BID, 150.25, 100));
        book.addOrder(new Order(Order.Type.BID, 150.25, 50));
        book.addOrder(new Order(Order.Type.BID, 149.75, 200));

        // Asks (Sellers)
        book.addOrder(new Order(Order.Type.ASK, 150.00, 75));
        book.addOrder(new Order(Order.Type.ASK, 150.00, 100));

        // Executes Math-Based Matching.
        book.matchOrders();


        System.out.println("JavaHFTDemo MVP deployed by Anthony E. Lewallen");
        System.out.println("Github: https://github.com/LewallenAE/JavaHFT");

    } // end of the main method
} // end Demo class
