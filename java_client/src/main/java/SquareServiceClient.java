import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.json.JSONObject;

/**
 * Java client for the Square Number REST web service (tutorial layout).
 *
 * <p><b>Compile and run</b> (JAR in same folder as the source file, as in the PDF):</p>
 * <pre>{@code
 * # Linux / macOS — interactive mode
 * javac -cp .:json-20231013.jar SquareServiceClient.java
 * java -cp .:json-20231013.jar SquareServiceClient
 *
 * # Single use (e.g. square 7)
 * java -cp .:json-20231013.jar SquareServiceClient 7
 * }</pre>
 * <p>On Windows use {@code ;} instead of {@code :} in {@code -cp}. This repo keeps sources under
 * {@code src/main/java}; use {@code javac -cp lib/json-20231013.jar -d target/classes ...} or Maven.</p>
 * <p>Optional environment: {@code SQUARE_SERVER_URL}, {@code SQUARE_API_KEY} ({@code X-API-Key}).</p>
 * <p>Tests: {@code mvn test}, or on Windows without Maven run {@code .\run-tests.ps1} in {@code java_client}.</p>
 */
public class SquareServiceClient {

    private static volatile String testBaseUrlOverride;

    private static String baseUrl() {
        String overridden = testBaseUrlOverride;
        if (overridden != null) {
            return overridden;
        }
        return resolveServerUrlFromEnvironment();
    }

    /** JUnit: {@code null} restores normal env/default URL resolution. */
    static void setBaseUrlOverrideForTesting(String baseUrlWithoutTrailingSlash) {
        testBaseUrlOverride =
                baseUrlWithoutTrailingSlash == null
                        ? null
                        : normalizeTrailingSlash(baseUrlWithoutTrailingSlash);
    }

    private static String resolveServerUrlFromEnvironment() {
        String env = System.getenv("SQUARE_SERVER_URL");
        if (env != null && !env.isBlank()) {
            return normalizeTrailingSlash(env);
        }
        return "http://localhost:5000";
    }

    private static String normalizeTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Checks if the server is running by calling the health endpoint.
     *
     * @return true if server is healthy, false otherwise
     */
    public static boolean checkServerHealth() {
        try {
            URL url = URI.create(baseUrl() + "/health").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    return "healthy".equals(jsonResponse.getString("status"));
                }
            }
            return false;
        } catch (Exception e) {
            System.out.println("Error checking server health: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends a request to square a number.
     *
     * @param number the number to square
     * @return the squared value or -1 if an error occurs
     */
    public static int squareNumber(int number) {
        try {
            URL url = URI.create(baseUrl() + "/square").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            String apiKey = System.getenv("SQUARE_API_KEY");
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("X-API-Key", apiKey);
            }

            JSONObject requestBody = new JSONObject();
            requestBody.put("number", number);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    return jsonResponse.getInt("square");
                }
            } else {
                System.out.println("Error: Server returned status code " + responseCode);
                String errText = readAll(conn.getErrorStream());
                System.out.println("Response: " + errText);
                return -1;
            }
        } catch (IOException e) {
            System.out.println("Error communicating with server: " + e.getMessage());
            return -1;
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line.trim());
            }
            return sb.toString();
        }
    }

    public static void main(String[] args) {
        if (!checkServerHealth()) {
            System.err.println(
                    "Error: Cannot connect to the square service. Is it running?");
            System.exit(1);
        }
        System.out.println("Connected to Square Service successfully!");

        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        try {
            if (args.length > 0) {
                try {
                    int number = Integer.parseInt(args[0].trim());
                    int square = squareNumber(number);
                    if (square != -1) {
                        System.out.printf(
                                "The square of %d is %d%n", number, square);
                    }
                } catch (NumberFormatException e) {
                    System.err.println(
                            "Error: Please provide a valid integer value");
                }
            } else {
                interactiveMode(scanner);
            }
        } finally {
            scanner.close();
        }
        System.out.println("Goodbye!");
    }

    private static void interactiveMode(Scanner scanner) {
        while (true) {
            try {
                System.out.print("\nEnter an integer to square (or 'q' to quit): ");
                String userInput = scanner.nextLine().trim();
                if (userInput.equalsIgnoreCase("q")) {
                    break;
                }
                int number = Integer.parseInt(userInput);
                int square = squareNumber(number);
                if (square != -1) {
                    System.out.printf("The square of %d is %d%n", number, square);
                }
            } catch (NumberFormatException e) {
                System.err.println("Error: Please enter a valid integer");
            }
        }
    }
}
