# JavaHFT — Central Limit Order Book

A Central Limit Order Book (CLOB) implementation in Java with price-time priority matching, order cancellation, market orders, and a set of simple trading strategies backtested against live market data.

**Anthony E. Lewallen**
Mathematics (B.S.) | MCIT @ UPenn

---

## What This Is

A learning project built during the MCIT program to apply data structures and systems concepts to a real financial engineering problem. The core focus is the order book engine and matching algorithm; the strategies and backtester are a simple demonstration layer on top.

---

## Order Book Engine

The CLOB lives in `OrderBook.java` and is built around two sorted maps:

```
TreeMap<Long, LinkedList<Order>>  bids   // descending — best bid first
TreeMap<Long, LinkedList<Order>>  asks   // ascending  — best ask first
```

Prices are stored as **integer ticks** (`long`, where 1 tick = $0.01) rather than `double`. This avoids floating-point equality bugs that would cause `TreeMap` key lookups to silently fail at certain price points.

### Matching algorithm

1. Peek at `bids.firstKey()` and `asks.firstKey()`
2. If best bid tick >= best ask tick, a match is possible
3. Execute at the midpoint price; fill both orders
4. Remove any fully-filled orders and empty price levels
5. Repeat until the spread is negative or either side is empty

### Order types

| Type | Behaviour |
|------|-----------|
| Limit | Rests in the book at the given price level |
| Market | Walks the book immediately, partially fills if insufficient liquidity |

### Cancellation

Orders are indexed by ID in a `HashMap<Long, OrderLocation>` for O(1) cancel lookup without scanning the book.

---

## Strategies

Three simple strategies run against historical data fetched via the Yahoo Finance API:

| Strategy | Logic |
|----------|-------|
| `MovingAverageCrossoverStrategy` | Short MA crosses long MA |
| `MeanReversionStrategy` | Price deviates from rolling mean |
| `VWAPStrategy` | Price vs. session VWAP |

These are illustrative; they are not optimized or risk-managed.

---

## Backtester

`BacktestEngine.java` replays historical quotes through a chosen strategy and tracks P&L, win rate, and trade count. `PortfolioTracker.java` maintains running position and cash.

---

## Running

**Prerequisites:** Java 17+, Maven

```bash
mvn compile
mvn exec:java -Dexec.mainClass="JavaHFTDemo"
```

**Tests:**

```bash
mvn test
```

---

## Data Structures Applied

| Concept | Where |
|---------|-------|
| TreeMap (sorted map) | Price level ordering in `OrderBook` |
| LinkedList (FIFO queue) | Order queue within each price level |
| HashMap | O(1) order lookup by ID (`orderIndex`) |
| AtomicLong | Thread-safe order ID generation |
| Comparable | Price-time priority in `Order.compareTo()` |

---

## Project Structure

```
src/
  main/java/
    Order.java               — Order model + tick-based price representation
    OrderBook.java           — CLOB matching engine
    OrderLocation.java       — Order index entry for O(1) cancellation
    Trade.java               — Immutable trade record
    TradingStrategy.java     — Strategy interface
    MovingAverageCrossoverStrategy.java
    MeanReversionStrategy.java
    VWAPStrategy.java
    BacktestEngine.java      — Historical replay engine
    PortfolioTracker.java    — P&L and position tracking
    MarketDataFetcher.java   — Yahoo Finance data fetch
    StockQuote.java          — Quote model
    TradingSignal.java       — Signal enum (BUY / SELL / HOLD)
    ChartRenderer.java       — ASCII chart output
    HFTSystem.java           — Wires everything together
    JavaHFTDemo.java         — Entry point
  test/java/
    OrderBookTest.java
```

---

## Reading That Helped

- *Trading and Exchanges* by Larry Harris (market microstructure)
- *Algorithmic Trading* by Ernie Chan (strategy design)
- *Java Concurrency in Practice* by Brian Goetz (thread-safe patterns)
- Parlour & Seppi (2008), "Limit Order Markets: A Survey"

---

**MIT License | Not financial advice | Not production software**
