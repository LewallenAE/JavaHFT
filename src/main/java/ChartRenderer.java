import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Generates PNG charts for price data, signals, and equity curves.
 * Uses JFreeChart for professional-quality visualizations.
 * Also provides ASCII charts for terminal output.
 *
 * @author Anthony Lewallen
 */
public class ChartRenderer {

    private static final int CHART_WIDTH = 1200;
    private static final int CHART_HEIGHT = 600;

    /**
     * Generate a price chart with buy/sell signals overlaid.
     */
    public static void generatePriceChart(BacktestEngine.BacktestResult result, String outputPath) {
        TimeSeries priceSeries = new TimeSeries("Price");
        for (StockQuote q : result.quotes) {
            priceSeries.addOrUpdate(
                    new Day(q.getDate().getDayOfMonth(), q.getDate().getMonthValue(), q.getDate().getYear()),
                    q.getClose()
            );
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(priceSeries);
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                result.symbol + " - " + result.strategyName,
                "Date", "Price ($)", dataset, true, true, false
        );

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, new Color(0, 100, 180));
        renderer.setSeriesStroke(0, new BasicStroke(1.5f));
        plot.setRenderer(renderer);

        // Add buy/sell signal annotations
        for (TradingSignal signal : result.signals) {
            if (signal.getAction() == TradingSignal.Action.HOLD) continue;

            Day day = new Day(signal.getDate().getDayOfMonth(),
                    signal.getDate().getMonthValue(), signal.getDate().getYear());
            long millis = day.getFirstMillisecond();

            String label = signal.getAction() == TradingSignal.Action.BUY ? "B" : "S";
            XYTextAnnotation annotation = new XYTextAnnotation(label, millis, signal.getPrice());
            annotation.setPaint(signal.getAction() == TradingSignal.Action.BUY
                    ? new Color(0, 150, 0) : new Color(200, 0, 0));
            annotation.setFont(new Font("SansSerif", Font.BOLD, 12));
            plot.addAnnotation(annotation);
        }

        saveChart(chart, outputPath);
    }

    /**
     * Generate an equity curve chart.
     */
    public static void generateEquityChart(BacktestEngine.BacktestResult result, String outputPath) {
        TimeSeries equitySeries = new TimeSeries("Portfolio Value");
        List<PortfolioTracker.EquityPoint> curve = result.portfolio.getEquityCurve();

        for (PortfolioTracker.EquityPoint point : curve) {
            if (point.date == null) continue;
            equitySeries.addOrUpdate(
                    new Day(point.date.getDayOfMonth(), point.date.getMonthValue(), point.date.getYear()),
                    point.value
            );
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(equitySeries);
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                "Equity Curve - " + result.strategyName,
                "Date", "Portfolio Value ($)", dataset, true, true, false
        );

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, new Color(0, 150, 50));
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        plot.setRenderer(renderer);

        saveChart(chart, outputPath);
    }

    /**
     * Generate comparison chart for multiple strategies.
     */
    public static void generateComparisonChart(List<BacktestEngine.BacktestResult> results, String outputPath) {
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        Color[] colors = {
                new Color(0, 100, 180), new Color(200, 0, 0),
                new Color(0, 150, 50), new Color(180, 120, 0)
        };

        for (BacktestEngine.BacktestResult result : results) {
            TimeSeries series = new TimeSeries(result.strategyName);
            for (PortfolioTracker.EquityPoint point : result.portfolio.getEquityCurve()) {
                if (point.date == null) continue;
                series.addOrUpdate(
                        new Day(point.date.getDayOfMonth(), point.date.getMonthValue(), point.date.getYear()),
                        point.value
                );
            }
            dataset.addSeries(series);
        }

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                "Strategy Comparison - " + results.get(0).symbol,
                "Date", "Portfolio Value ($)", dataset, true, true, false
        );

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        for (int i = 0; i < results.size(); i++) {
            renderer.setSeriesPaint(i, colors[i % colors.length]);
            renderer.setSeriesStroke(i, new BasicStroke(2.0f));
        }
        plot.setRenderer(renderer);

        saveChart(chart, outputPath);
    }

    private static void saveChart(JFreeChart chart, String outputPath) {
        try {
            File file = new File(outputPath);
            file.getParentFile().mkdirs();
            ChartUtils.saveChartAsPNG(file, chart, CHART_WIDTH, CHART_HEIGHT);
            System.out.println("Chart saved: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save chart: " + e.getMessage());
        }
    }

    // ============================================================
    //  ASCII Charts (Terminal Fallback)
    // ============================================================

    /**
     * Print an ASCII price chart to the terminal.
     */
    public static void printAsciiPriceChart(List<StockQuote> quotes, List<TradingSignal> signals, int width) {
        if (quotes.isEmpty()) return;

        int height = 20;
        double minPrice = quotes.stream().mapToDouble(StockQuote::getLow).min().orElse(0);
        double maxPrice = quotes.stream().mapToDouble(StockQuote::getHigh).max().orElse(100);
        double range = maxPrice - minPrice;
        if (range == 0) range = 1;

        // Sample quotes to fit width
        int step = Math.max(1, quotes.size() / width);
        List<StockQuote> sampled = new java.util.ArrayList<>();
        for (int i = 0; i < quotes.size(); i += step) {
            sampled.add(quotes.get(i));
        }

        // Build signal lookup by date
        java.util.Map<java.time.LocalDate, TradingSignal> signalMap = new java.util.HashMap<>();
        if (signals != null) {
            for (TradingSignal s : signals) {
                signalMap.put(s.getDate(), s);
            }
        }

        System.out.println("\n--- PRICE CHART ---");
        System.out.printf("  $%.2f |", maxPrice);

        // Build the chart grid
        char[][] grid = new char[height][sampled.size()];
        for (char[] row : grid) java.util.Arrays.fill(row, ' ');

        for (int col = 0; col < sampled.size(); col++) {
            StockQuote q = sampled.get(col);
            int row = height - 1 - (int) ((q.getClose() - minPrice) / range * (height - 1));
            row = Math.max(0, Math.min(height - 1, row));

            TradingSignal sig = signalMap.get(q.getDate());
            if (sig != null && sig.getAction() == TradingSignal.Action.BUY) {
                grid[row][col] = 'B';
            } else if (sig != null && sig.getAction() == TradingSignal.Action.SELL) {
                grid[row][col] = 'S';
            } else {
                grid[row][col] = '*';
            }
        }

        // Print grid
        for (int row = 0; row < height; row++) {
            if (row == 0) {
                System.out.printf("  $%7.2f |", maxPrice);
            } else if (row == height - 1) {
                System.out.printf("  $%7.2f |", minPrice);
            } else if (row == height / 2) {
                double midPrice = (maxPrice + minPrice) / 2;
                System.out.printf("  $%7.2f |", midPrice);
            } else {
                System.out.print("           |");
            }
            for (int col = 0; col < sampled.size(); col++) {
                System.out.print(grid[row][col]);
            }
            System.out.println();
        }
        System.out.print("           +");
        for (int i = 0; i < sampled.size(); i++) System.out.print('-');
        System.out.println();

        if (!sampled.isEmpty()) {
            System.out.printf("            %s", sampled.get(0).getDate());
            int pad = sampled.size() - 20;
            if (pad > 0) {
                for (int i = 0; i < pad; i++) System.out.print(' ');
            }
            System.out.printf("%s\n", sampled.get(sampled.size() - 1).getDate());
        }
        System.out.println("  Legend: * = price, B = BUY signal, S = SELL signal");
    }

    /**
     * Print ASCII equity curve.
     */
    public static void printAsciiEquityCurve(PortfolioTracker portfolio, int width) {
        List<PortfolioTracker.EquityPoint> curve = portfolio.getEquityCurve();
        if (curve.size() < 2) return;

        int height = 15;
        double minVal = curve.stream().mapToDouble(p -> p.value).min().orElse(0);
        double maxVal = curve.stream().mapToDouble(p -> p.value).max().orElse(100);
        double range = maxVal - minVal;
        if (range == 0) range = 1;

        int step = Math.max(1, curve.size() / width);

        System.out.println("\n--- EQUITY CURVE ---");

        char[][] grid = new char[height][width];
        for (char[] row : grid) java.util.Arrays.fill(row, ' ');

        int col = 0;
        for (int i = 0; i < curve.size() && col < width; i += step) {
            int row = height - 1 - (int) ((curve.get(i).value - minVal) / range * (height - 1));
            row = Math.max(0, Math.min(height - 1, row));
            grid[row][col] = '#';
            col++;
        }

        for (int row = 0; row < height; row++) {
            if (row == 0) {
                System.out.printf("  $%,10.0f |", maxVal);
            } else if (row == height - 1) {
                System.out.printf("  $%,10.0f |", minVal);
            } else {
                System.out.print("             |");
            }
            for (int c = 0; c < col; c++) {
                System.out.print(grid[row][c]);
            }
            System.out.println();
        }
        System.out.print("             +");
        for (int i = 0; i < col; i++) System.out.print('-');
        System.out.println();
    }
}
