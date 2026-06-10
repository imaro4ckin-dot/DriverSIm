package org.example.drivesimui;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Connects to a local Ollama AI server to analyze telemetry data.
 */
public class AIClient {

    // Ollama's default local address
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL_NAME = "llama3"; // Change to "llama3" if you downloaded that one

    public String askEngineer(String promptText) {
        try {
            // 1. Clean the prompt so quotes or newlines don't break the JSON structure
            String safePrompt = promptText.replace("\"", "'").replace("\n", " ");

            // 2. Build the JSON payload exactly how Ollama expects it
            String jsonInput = "{\n" +
                    "  \"model\": \"" + MODEL_NAME + "\",\n" +
                    "  \"prompt\": \"" + safePrompt + "\",\n" +
                    "  \"stream\": false\n" +
                    "}";

            // 3. Set up the Java HTTP Client
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonInput))
                    .build();

            // 4. Send the request to your local AI and wait for the reply
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 5. Parse the JSON response to get just the text
            return extractAIResponse(response.body());

        } catch (Exception e) {
            return "Radio failure: Could not connect to the AI Engineer. Is Ollama running in your terminal?";
        }
    }

    /**
     * A clever workaround to extract the AI's text from the JSON string
     * without needing external libraries like Jackson or Gson.
     */
    /**
     * A safer workaround to extract the AI's text from the JSON string.
     */
    private String extractAIResponse(String json) {
        // 1. Print the raw JSON to your IDE console so you can see exactly what Ollama sent!
        System.out.println("=== RAW OLLAMA DATA ===");
        System.out.println(json);
        System.out.println("=======================");

        try {
            // 2. Check if Ollama is yelling at us
            if (json.contains("\"error\"")) {
                return "Ollama Error! Look at your IntelliJ console to see what went wrong.";
            }

            // 3. A much safer way to split the text
            String[] parts = json.split("\"response\":\"");
            if (parts.length > 1) {
                String text = parts[1];

                // Find where the response ends (right before the "done" flag)
                int endIndex = text.lastIndexOf("\"done\"");

                if (endIndex != -1) {
                    // Cut off the trailing characters before the word "done"
                    text = text.substring(0, endIndex - 2).trim();

                    // Fix newlines and quotes so it looks pretty in the UI
                    return text.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
                }
            }
        } catch (Exception e) {
            System.err.println("Parsing failed: " + e.getMessage());
        }

        return "Could not parse JSON. Check your IDE console to see the raw data.";
    }
}