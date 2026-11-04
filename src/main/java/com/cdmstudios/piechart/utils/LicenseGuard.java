package com.cdmstudios.piechart.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class LicenseGuard {
    private static final Pattern REJECTED_RESPONSE = Pattern.compile("\\\"(?:valid|allowed)\\\"\\s*:\\s*false", Pattern.CASE_INSENSITIVE);
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\\\"error\\\"\\s*:\\s*\\\"([^\\\"]{1,160})\\\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESPONSE_MESSAGE = Pattern.compile("\\\"(?:error|message|reason)\\\"\\s*:\\s*\\\"([^\\\"]{1,160})\\\"", Pattern.CASE_INSENSITIVE);

    private LicenseGuard() {
    }

    public static String verify(String apiUrl, String productSlug, String licenseKey) throws IOException {
        URL url = new URL(apiUrl);
        if (!(url.openConnection() instanceof HttpURLConnection connection)) {
            throw new IOException("License API must use HTTP or HTTPS.");
        }

        String fingerprint;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(System.getenv().getOrDefault("COMPUTERNAME", System.getenv().getOrDefault("HOSTNAME", "unknown"))
                    .getBytes(StandardCharsets.UTF_8));
            fingerprint = HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable.", exception);
        }
        String json = "{\"licenseKey\":\"" + escapeJson(licenseKey)
            + "\",\"productSlug\":\"" + escapeJson(productSlug)
            + "\",\"fingerprint\":\"" + fingerprint + "\"}";

        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            byte[] requestBody = json.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(requestBody.length);
            try (var output = connection.getOutputStream()) {
                output.write(requestBody);
            }

            int statusCode = connection.getResponseCode();
            InputStream responseStream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String response = "";
            if (responseStream != null) {
                try (var input = responseStream) {
                    response = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }

            String message = responseMessage(response);
            if (statusCode != 200) {
                return "API returned HTTP " + statusCode + (message == null ? "" : ": " + message);
            }
            String error = errorMessage(response);
            if (REJECTED_RESPONSE.matcher(response).find() || error != null) {
                return "API rejected the license" + (message == null ? "" : ": " + message);
            }
            return null;
        } finally {
            connection.disconnect();
        }
    }

    private static String responseMessage(String response) {
        var matcher = RESPONSE_MESSAGE.matcher(response);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String errorMessage(String response) {
        var matcher = ERROR_MESSAGE.matcher(response);
        if (!matcher.find() || matcher.group(1).isBlank()) {
            return null;
        }
        return matcher.group(1);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
