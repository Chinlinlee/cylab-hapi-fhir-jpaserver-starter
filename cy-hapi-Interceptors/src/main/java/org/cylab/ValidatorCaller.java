package org.cylab;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ValidatorCaller {
    public ValidationResponseResult sendJsonRequest(String targetUrl, String jsonPayload) {
        HttpURLConnection connection = null;
        StringBuilder response = new StringBuilder();
        int statusCode = -1;

        try {
            // 建立 URL 連線
            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();

            // 設定請求屬性
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            // 傳送 JSON 請求
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 取得回應狀態碼
            statusCode = connection.getResponseCode();

            // 接收回應
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ValidationResponseResult("Error: " + e.getMessage(), statusCode);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return new ValidationResponseResult(response.toString(), statusCode);
    }
}
