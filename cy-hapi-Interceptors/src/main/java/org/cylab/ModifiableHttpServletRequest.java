package org.cylab;

import jakarta.servlet.ReadListener;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.ServletInputStream;

public class ModifiableHttpServletRequest extends HttpServletRequestWrapper {
    private byte[] body;

    public ModifiableHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        // 讀取原始的 request body
        body = IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8).getBytes();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);

        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }

            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    public void setBody(byte[] newBody) {
        this.body = newBody;
    }

    public byte[] getBody() {
        return this.body;
    }
}
