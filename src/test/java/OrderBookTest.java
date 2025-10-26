import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Unit tests for OrderBook - Market Orders
 */
public class OrderBookTest {

    private OrderBook book;

    @BeforeEach
    public void setUp() {
        book = new OrderBook();
    }

    @Test
    @DisplayName("Market buy order should fully fill when sufficient liquidity exists")
    public void testMarketBuyFullFill() {
        // Given: Order book with ask liquidity
        book.addOrder(new Order(Order.Type.ASK, 151.00, 50));
        book.addOrder(new Order(Order.Type.ASK, 152.00, 100));

        // When: Market buy for 100 shares
        Order marketBuy = Order.createMarketOrder(Order.Type.BID, 100);
        book.addOrder(marketBuy);

        // Then: Order should be fully filled
        assertTrue(marketBuy.isFilled(), "Market order should be fully filled");
        assertEquals(Order.Status.FILLED, marketBuy.getStatus());
        assertEquals(0, marketBuy.getRemainingQuantity());
    }

    @Test
    @DisplayName("Market buy should partially fill when insufficient liquidity")
    public void testMarketBuyPartialFill() {
        // Given: Limited liquidity
        book.addOrder(new Order(Order.Type.ASK, 151.00, 50));

        // When: Market buy for more than available
        Order marketBuy = Order.createMarketOrder(Order.Type.BID, 200);
        book.addOrder(marketBuy);

        // Then: Should partially fill
        assertFalse(marketBuy.isFilled(), "Market order should NOT be fully filled");
        assertEquals(150, marketBuy.getRemainingQuantity());
        assertEquals(Order.Status.PARTIAL_FILL, marketBuy.getStatus());
    }

    @Test
    @DisplayName("Market order on empty book should not fill")
    public void testMarketOrderEmptyBook() {
        // Given: Empty order book

        // When: Market buy arrives
        Order marketBuy = Order.createMarketOrder(Order.Type.BID, 100);
        book.addOrder(marketBuy);

        // Then: No fills
        assertEquals(100, marketBuy.getRemainingQuantity());
        assertEquals(Order.Status.REJECTED, marketBuy.getStatus());
    }

    @Test
    @DisplayName("Market order should walk through multiple price levels")
    public void testMarketOrderMultiplePriceLevels() {
        // Given: Multiple ask price levels
        book.addOrder(new Order(Order.Type.ASK, 151.00, 50));
        book.addOrder(new Order(Order.Type.ASK, 152.00, 50));
        book.addOrder(new Order(Order.Type.ASK, 153.00, 50));

        // When: Market buy for 120 shares
        Order marketBuy = Order.createMarketOrder(Order.Type.BID, 120);
        List<Trade> trades = book.executeMarketOrder(marketBuy);

        // Then: Should create 3 trades at different prices
        assertEquals(3, trades.size(), "Should have 3 trades");
        assertEquals(0, marketBuy.getRemainingQuantity(), "Should have 0 remaining");

        // Verify trade prices
        assertEquals(151.00, trades.get(0).getPrice());
        assertEquals(152.00, trades.get(1).getPrice());
        assertEquals(153.00, trades.get(2).getPrice());
    }

    @Test
    @DisplayName("Market sell should hit best bids first")
    public void testMarketSell() {
        // Given: Bid liquidity
        book.addOrder(new Order(Order.Type.BID, 150.00, 100));
        book.addOrder(new Order(Order.Type.BID, 149.00, 100));

        // When: Market sell for 150 shares
        Order marketSell = Order.createMarketOrder(Order.Type.ASK, 150);
        book.addOrder(marketSell);

        // Then: Should fully fill
        assertTrue(marketSell.isFilled());
        assertEquals(Order.Status.FILLED, marketSell.getStatus());
    }

    @Test
    @DisplayName("Market order should remove filled orders from book")
    public void testMarketOrderCleansUpBook() {
        // Given: Single ask order
        book.addOrder(new Order(Order.Type.ASK, 151.00, 50));

        // When: Market buy that fully consumes the ask
        Order marketBuy = Order.createMarketOrder(Order.Type.BID, 50);
        book.addOrder(marketBuy);

        // Then: Ask side should be empty
        assertTrue(book.getAskDepth().isEmpty(), "Ask side should be empty");
    }
}