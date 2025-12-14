package com.email.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
public class EmailGeneratorService {

    private final WebClient webClient;
    private final String apiKey;

    public EmailGeneratorService(WebClient.Builder webClientBuilder,
                                 @Value("${gemini.api.url}") String baseUrl,
                                 @Value("${gemini.api.key}") String geminiApiKey) {

        this.apiKey = geminiApiKey;
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    public String generateEmail(EmailRequest emailRequest) {

        String prompt = buildPrompt(emailRequest);

        // ---------------------------
        // JSON STRING (SAFE VERSION)
        // ---------------------------
        ObjectMapper mapper = new ObjectMapper();

        String requestBody;
        try {
            requestBody = mapper.writeValueAsString(
                    Map.of(
                            "contents", List.of(
                                    Map.of(
                                            "parts", List.of(
                                                    Map.of("text", prompt)
                                            )
                                    )
                            )
                    )
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON conversion error", e);
        }

        System.out.println("FINAL OUTGOING JSON --> " + requestBody);

        try {
            // ---------------------------
            // API CALL
            // ---------------------------
            String response = webClient.post()
                    .uri("v1beta/models/gemini-2.5-flash:generateContent")
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)          // JSON STRING
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return extractResponseContent(response);

        } catch (WebClientResponseException ex) {
            // PRINT FULL ERROR FOR DEBUGGING
            System.err.println("STATUS CODE: " + ex.getStatusCode());
            System.err.println("API RESPONSE BODY: " + ex.getResponseBodyAsString());
            throw new RuntimeException("Gemini API Error", ex);

        } catch (Exception e) {
            throw new RuntimeException("Unknown Error", e);
        }
    }

    private String extractResponseContent(String response) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            var root = objectMapper.readTree(response);
            return root.path("candidates").get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

        } catch (Exception e) {
            System.err.println("Response Parsing Error → " + e.getMessage());
            throw new RuntimeException("Response parsing failed!", e);
        }
    }

    private String buildPrompt(EmailRequest emailRequest) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Write a reply to the following email.\n");

        if (emailRequest.getTone() != null && !emailRequest.getTone().isEmpty()) {
            prompt.append("Use a ").append(emailRequest.getTone()).append(" tone.\n");
        }

        prompt.append("Email Content:\n")
                .append(emailRequest.getEmailContent())
                .append("\n\nReply:\n");

        return prompt.toString();
    }
}
