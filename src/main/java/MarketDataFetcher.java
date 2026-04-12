import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches real market data from Yahoo Finance (free, no API key required).
 * Supports historical OHLCV data for any publicly traded symbol.
 *
 * @author Anthony Lewallen
 */
public class MarketDataFetcher {

    private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MarketDataFetcher() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetch historical daily quotes for a symbol.
     *
     * @param symbol ticker symbol (e.g., "AAPL", "MSFT", "SPY")
     * @param range  time range: "1mo", "3mo", "6mo", "1y", "2y", "5y"
     * @return list of StockQuote objects (oldest first)
     */
    public List<StockQuote> fetchHistoricalData(String symbol, String range) throws Exception {
        return fetchData(symbol, range, "1d");
    }

    /**
     * Fetch intraday quotes (5-minute intervals) for a symbol.
     *
     * @param symbol ticker symbol
     * @return list of StockQuote objects for the current/last trading day
     */
    public List<StockQuote> fetchIntradayData(String symbol) throws Exception {
        return fetchData(symbol, "1d", "5m");
    }

    /**
     * Fetch the latest quote for a symbol.
     *
     * @param symbol ticker symbol
     * @return the most recent StockQuote
     */
    public StockQuote fetchLatestQuote(String symbol) throws Exception {
        List<StockQuote> quotes = fetchData(symbol, "5d", "1d");
        if (quotes.isEmpty()) {
            throw new RuntimeException("No data returned for symbol: " + symbol);
        }
        return quotes.get(quotes.size() - 1);
    }

    private List<StockQuote> fetchData(String symbol, String range, String interval) throws Exception {
        String url = BASE_URL + symbol + "?range=" + range + "&interval=" + interval
                + "&includePrePost=false&events=";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Yahoo Finance API returned status " + response.statusCode()
                    + " for symbol: " + symbol);
        }

        return parseResponse(response.body());
    }

    private List<StockQuote> parseResponse(String json) throws Exception {
        List<StockQuote> quotes = new ArrayList<>();

        JsonNode root = objectMapper.readTree(json);
        JsonNode chart = root.path("chart").path("result").get(0);

        if (chart == null) {
            throw new RuntimeException("Invalid response from Yahoo Finance API");
        }

        JsonNode timestamps = chart.path("timestamp");
        JsonNode indicators = chart.path("indicators").path("quote").get(0);

        JsonNode opens = indicators.path("open");
        JsonNode highs = indicators.path("high");
        JsonNode lows = indicators.path("low");
        JsonNode closes = indicators.path("close");
        JsonNode volumes = indicators.path("volume");

        for (int i = 0; i < timestamps.size(); i++) {
            // Skip null data points
            if (closes.get(i).isNull() || opens.get(i).isNull()) {
                continue;
            }

            long epochSeconds = timestamps.get(i).asLong();
            LocalDate date = Instant.ofEpochSecond(epochSeconds)
                    .atZone(ZoneId.of("America/New_York"))
                    .toLocalDate();

            double open = opens.get(i).asDouble();
            double high = highs.get(i).asDouble();
            double low = lows.get(i).asDouble();
            double close = closes.get(i).asDouble();
            long volume = volumes.get(i).isNull() ? 0 : volumes.get(i).asLong();

            quotes.add(new StockQuote(date, open, high, low, close, volume));
        }

        return quotes;
    }

    /**
     * Fetch data for multiple symbols at once.
     *
     * @param symbols list of ticker symbols
     * @param range   time range
     * @return list of quote lists, one per symbol
     */
    public List<List<StockQuote>> fetchMultipleSymbols(List<String> symbols, String range) {
        List<List<StockQuote>> allData = new ArrayList<>();
        for (String symbol : symbols) {
            try {
                allData.add(fetchHistoricalData(symbol, range));
                // Small delay to avoid rate limiting
                Thread.sleep(200);
            } catch (Exception e) {
                System.err.printf("Failed to fetch data for %s: %s\n", symbol, e.getMessage());
                allData.add(new ArrayList<>());
            }
        }
        return allData;
    }
}
