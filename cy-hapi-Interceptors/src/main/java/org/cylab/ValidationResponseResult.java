package org.cylab;

public class ValidationResponseResult {
    private String body;
    private int status;

    public ValidationResponseResult(String body, int status) {
        this.body = body;
        this.status = status;
    }

    public String getBody() {
        return body;
    }

    public int getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "Status: " + status + ", Body: " + body;
    }
}
