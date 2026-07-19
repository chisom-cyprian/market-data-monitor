import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;

/**
 * MarketDataMonitor
 *
 * Polls the Twelve Data API for the latest price of a given symbol
 * (default: DIA, an ETF that closely tracks the Dow Jones Industrial
 * Average) at a fixed interval, and stores each result (price + UTC
 * timestamp) in an in-memory queue.
 *
 * Intended as a lightweight internal tool for near-real-time market
 * monitoring, e.g. by non-technical stakeholders viewing a downstream
 * dashboard or chart built from the collected data.
 *
 * Usage:
 *   export TWELVE_DATA_API_KEY=your_key_here
 *   javac MarketDataMonitor.java
 *   java MarketDataMonitor
 */
public class MarketDataMonitor {

    /** A single price observation at a point in time. */
    static class DataPoint {
        final double price;
        final String timestamp;

        DataPoint(double price, String timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "price=" + price + ", timestamp=" + timestamp;
        }
    }

    private static final Queue<DataPoint> dataQueue = new LinkedList<>();

    private static final long POLL_INTERVAL_MS = 15_000;
    private static final String DEFAULT_SYMBOL = "DIA";
    private static final String API_BASE_URL = "https://api.twelvedata.com/price";

    public static void main(String[] args) {
        String apiKey = System.getenv("TWELVE_DATA_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("Error: TWELVE_DATA_API_KEY environment variable not set.");
            System.out.println("Set it with: export TWELVE_DATA_API_KEY=your_key_here");
            return;
        }

        String symbol = args.length > 0 ? args[0] : DEFAULT_SYMBOL;

        System.out.println("Starting market data monitor for symbol: " + symbol);
        System.out.println("Polling every " + (POLL_INTERVAL_MS / 1000) + " seconds. Press Ctrl+C to stop.");

        while (true) {
            try {
                double price = fetchLatestPrice(symbol, apiKey);
                String timestamp = Instant.now().toString();

                DataPoint point = new DataPoint(price, timestamp);
                dataQueue.add(point);

                System.out.println("Added data point: " + point);
                System.out.println("Current queue size: " + dataQueue.size());

            } catch (RateLimitException e) {
                System.out.println("Rate limit hit, backing off: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Error retrieving data: " + e.getMessage());
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Calls the Twelve Data API and returns the latest price for the given symbol.
     *
     * Example endpoint:
     *   https://api.twelvedata.com/price?symbol=DIA&apikey=YOUR_API_KEY
     * Example success response:
     *   {"price":"420.15"}
     * Example rate-limit response:
     *   {"code":429,"message":"You have run out of API credits..."}
     */
    private static double fetchLatestPrice(String symbol, String apiKey) throws Exception {
        String urlString = API_BASE_URL + "?symbol=" + symbol + "&apikey=" + apiKey;

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);

        int status = connection.getResponseCode();
        StringBuilder response = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(status >= 400 ? connection.getErrorStream() : connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } finally {
            connection.disconnect();
        }

        String body = response.toString();

        if (body.contains("\"code\":429") || status == 429) {
            throw new RateLimitException("Twelve Data API rate limit exceeded");
        }

        if (status != 200) {
            throw new RuntimeException("API request failed (HTTP " + status + "): " + body);
        }

        return parsePrice(body);
    }

    /**
     * Extracts the numeric price value from a simple JSON response like:
     * {"price":"420.15"}
     *
     * Note: this is a minimal hand-rolled parser to avoid an external
     * dependency, since this project targets sandboxed environments
     * (e.g. Google Colab) where adding a JSON library to the classpath
     * is inconvenient. For a production deployment, swap this out for
     * a proper JSON library such as org.json or Jackson.
     */
    private static double parsePrice(String json) {
        String key = "\"price\":\"";
        int start = json.indexOf(key);
        if (start == -1) {
            throw new RuntimeException("Unexpected API response: " + json);
        }
        start += key.length();
        int end = json.indexOf("\"", start);
        String priceStr = json.substring(start, end);
        return Double.parseDouble(priceStr);
    }

    /** Thrown when the API reports that the rate limit has been exceeded. */
    static class RateLimitException extends Exception {
        RateLimitException(String message) {
            super(message);
        }
    }
}
