/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.transport.servlet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.Bus;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

public class AbstractHTTPServletTest extends Assert {

    private static final String STATIC_CONTENT = "static content";

    @Test
    public void testStaticResourceIsServed() throws Exception {
        TestServlet servlet = createServlet(staticResourcesConfig(true));
        RecordingOutputStream os = new RecordingOutputStream();

        servlet.service(createRequest("/index.html", null), createResponse(os));

        assertFalse(servlet.isInvoked());
        assertEquals(Collections.singletonList("/index.html"), servlet.getRequestedResources());
        assertEquals(STATIC_CONTENT, os.getContent());
    }

    @Test
    public void testStaticResourcesWithRedirectQueryCheck() throws Exception {
        // GET /services/?.html : the query string must not be used to match static resources,
        // otherwise the servlet root ("/") is served, which exposes a directory listing
        TestServlet servlet = createServlet(staticResourcesConfig(true));
        RecordingOutputStream os = new RecordingOutputStream();

        servlet.service(createRequest("/", ".html"), createResponse(os));

        assertTrue(servlet.isInvoked());
        assertTrue(servlet.getRequestedResources().isEmpty());
        assertEquals("", os.getContent());
    }

    @Test
    public void testStaticResourcesWithRedirectQueryCheckNoPathInfo() throws Exception {
        // GET /services?.html
        TestServlet servlet = createServlet(staticResourcesConfig(true));
        RecordingOutputStream os = new RecordingOutputStream();

        servlet.service(createRequest(null, ".html"), createResponse(os));

        assertTrue(servlet.isInvoked());
        assertTrue(servlet.getRequestedResources().isEmpty());
        assertEquals("", os.getContent());
    }

    @Test
    public void testStaticResourcesWithRedirectQueryCheckClassFile() throws Exception {
        // GET /services/org/apache/cxf/Bus.class?a.html : no exfiltration of class files
        TestServlet servlet = createServlet(staticResourcesConfig(true));
        RecordingOutputStream os = new RecordingOutputStream();

        servlet.service(createRequest("/org/apache/cxf/Bus.class", "a.html"), createResponse(os));

        assertTrue(servlet.isInvoked());
        assertTrue(servlet.getRequestedResources().isEmpty());
        assertEquals("", os.getContent());
    }

    @Test
    public void testStaticResourcesWithoutRedirectQueryCheck() throws Exception {
        TestServlet servlet = createServlet(staticResourcesConfig(false));
        RecordingOutputStream os = new RecordingOutputStream();

        servlet.service(createRequest("/", ".html"), createResponse(os));

        assertTrue(servlet.isInvoked());
        assertTrue(servlet.getRequestedResources().isEmpty());
    }

    @Test
    public void testRedirectQueryCheckAppliesToRedirectList() throws Exception {
        HttpServletResponse response = createResponse(new RecordingOutputStream());
        ServletContext context = EasyMock.createMock(ServletContext.class);
        RequestDispatcher dispatcher = EasyMock.createMock(RequestDispatcher.class);
        EasyMock.expect(context.getNamedDispatcher("dispatcher")).andReturn(dispatcher).once();
        dispatcher.forward(EasyMock.isA(HttpServletRequest.class), EasyMock.same(response));
        EasyMock.expectLastCall().once();
        EasyMock.replay(context, dispatcher);

        Map<String, String> params = new HashMap<>();
        params.put("redirects-list", "/foo\\?bar=baz");
        params.put("redirect-servlet-name", "dispatcher");
        params.put("redirect-query-check", "true");
        TestServlet servlet = createServlet(new TestServletConfig(params, context));

        servlet.service(createRequest("/foo", "bar=baz"), response);
        assertFalse(servlet.isInvoked());

        servlet.service(createRequest("/foo", "bar=other"), response);
        assertTrue(servlet.isInvoked());

        EasyMock.verify(context, dispatcher);
    }

    private static TestServletConfig staticResourcesConfig(boolean redirectQueryCheck) {
        Map<String, String> params = new HashMap<>();
        params.put("static-resources-list", ".*\\.html");
        params.put("redirect-query-check", Boolean.toString(redirectQueryCheck));
        return new TestServletConfig(params, null);
    }

    private static TestServlet createServlet(ServletConfig config) throws Exception {
        TestServlet servlet = new TestServlet();
        servlet.init(config);
        return servlet;
    }

    private static HttpServletRequest createRequest(String pathInfo, String queryString) {
        HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(request.getMethod()).andReturn("GET").anyTimes();
        EasyMock.expect(request.getPathInfo()).andReturn(pathInfo).anyTimes();
        EasyMock.expect(request.getQueryString()).andReturn(queryString).anyTimes();
        EasyMock.replay(request);
        return request;
    }

    private static HttpServletResponse createResponse(ServletOutputStream os) throws Exception {
        HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
        EasyMock.expect(response.getOutputStream()).andReturn(os).anyTimes();
        EasyMock.replay(response);
        return response;
    }

    private static class TestServlet extends AbstractHTTPServlet {
        private static final long serialVersionUID = 1L;

        private final List<String> requestedResources = new ArrayList<>();
        private boolean invoked;

        @Override
        protected Bus getBus() {
            return null;
        }

        @Override
        protected void invoke(HttpServletRequest request, HttpServletResponse response) {
            invoked = true;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected InputStream getResourceAsStream(String path) {
            requestedResources.add(path);
            return new ByteArrayInputStream(STATIC_CONTENT.getBytes(StandardCharsets.UTF_8));
        }

        boolean isInvoked() {
            return invoked;
        }

        List<String> getRequestedResources() {
            return requestedResources;
        }
    }

    private static class TestServletConfig implements ServletConfig {
        private final Map<String, String> params;
        private final ServletContext context;

        TestServletConfig(Map<String, String> params, ServletContext context) {
            this.params = params;
            this.context = context;
        }

        public String getServletName() {
            return "test";
        }

        public ServletContext getServletContext() {
            return context;
        }

        public String getInitParameter(String name) {
            return params.get(name);
        }

        public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(params.keySet());
        }
    }

    private static class RecordingOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        @Override
        public void write(int b) {
            bos.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }

        String getContent() {
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
