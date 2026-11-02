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
package org.apache.cxf.transport.https;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLStreamHandler;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;

import org.apache.cxf.common.util.ReflectionUtil;
import org.apache.cxf.configuration.jsse.SSLUtils;
import org.apache.cxf.configuration.jsse.TLSClientParameters;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class HttpsURLConnectionFactoryTest {

    /**
     * Mirrors the deprecated com.sun.net.ssl.HostnameVerifier contract, whose only method is
     * verify(String urlHostname, String certHostname).
     */
    public interface LegacyHostnameVerifier {
        boolean verify(String urlHostname, String certHostname);
    }

    /**
     * A connection that is not a javax.net.ssl.HttpsURLConnection, but exposes the same "hidden"
     * HostnameVerifier / SSLSocketFactory accessors as the deprecated com.sun.net.ssl implementation.
     */
    public static class LegacyHttpsConnection extends HttpURLConnection {
        private LegacyHostnameVerifier hostnameVerifier = new LegacyHostnameVerifier() {
            public boolean verify(String urlHostname, String certHostname) {
                return false;
            }
        };
        private SSLSocketFactory sslSocketFactory;

        public LegacyHttpsConnection(URL url) {
            super(url);
        }

        public LegacyHostnameVerifier getHostnameVerifier() {
            return hostnameVerifier;
        }

        public void setHostnameVerifier(LegacyHostnameVerifier verifier) {
            this.hostnameVerifier = verifier;
        }

        public SSLSocketFactory getSSLSocketFactory() {
            return sslSocketFactory;
        }

        public void setSSLSocketFactory(SSLSocketFactory factory) {
            this.sslSocketFactory = factory;
        }

        @Override
        public void connect() {
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }
    }

    @Test
    public void deprecatedHostnameVerifierRejectsMismatchingCertHostname() throws Exception {
        TLSClientParameters tlsClientParams = new TLSClientParameters();
        tlsClientParams.setUseHttpsURLConnectionDefaultSslSocketFactory(true);

        LegacyHostnameVerifier verifier = decorateLegacyConnection(tlsClientParams);

        // The default CXF hostname verifier must not accept a certificate issued to another host
        Assert.assertFalse(verifier.verify("localhost", "bethal"));
        Assert.assertFalse(verifier.verify("www.apache.org", "attacker.example.com"));
        Assert.assertFalse(verifier.verify("a.foo.com", "foo.com"));
        Assert.assertFalse(verifier.verify("localhost", null));
        // ... but must still accept a matching one
        Assert.assertTrue(verifier.verify("localhost", "localhost"));
        Assert.assertTrue(verifier.verify("a.foo.com", "*.foo.com"));
    }

    @Test
    public void deprecatedHostnameVerifierFailsClosedForCustomVerifier() throws Exception {
        TLSClientParameters tlsClientParams = new TLSClientParameters();
        tlsClientParams.setUseHttpsURLConnectionDefaultSslSocketFactory(true);
        // A javax.net.ssl.HostnameVerifier without a verify(String, String) method
        tlsClientParams.setHostnameVerifier(new HostnameVerifier() {
            public boolean verify(String host, SSLSession session) {
                return true;
            }
        });

        LegacyHostnameVerifier verifier = decorateLegacyConnection(tlsClientParams);

        // The verification cannot be delegated, so it must fail rather than silently succeed
        Assert.assertFalse(verifier.verify("localhost", "bethal"));
        Assert.assertFalse(verifier.verify("localhost", "localhost"));
    }

    @Test
    public void deprecatedHostnameVerifierWithDisabledCNCheck() throws Exception {
        TLSClientParameters tlsClientParams = new TLSClientParameters();
        tlsClientParams.setUseHttpsURLConnectionDefaultSslSocketFactory(true);
        tlsClientParams.setDisableCNCheck(true);

        LegacyHostnameVerifier verifier = decorateLegacyConnection(tlsClientParams);

        Assert.assertTrue(verifier.verify("localhost", "bethal"));
        Assert.assertFalse(verifier.verify("localhost", ""));
        Assert.assertFalse(verifier.verify("localhost", null));
    }

    /**
     * Uses the JDK's deprecated "com.sun.net.ssl.internal.www.protocol" https handler, which is what is
     * picked up when "java.protocol.handler.pkgs" is set to "com.sun.net.ssl.internal.www.protocol".
     */
    @Test
    public void jdkDeprecatedHttpsConnectionRejectsMismatchingCertHostname() throws Exception {
        URLStreamHandler handler = null;
        try {
            handler = (URLStreamHandler)Class
                .forName("com.sun.net.ssl.internal.www.protocol.https.Handler").newInstance();
        } catch (ClassNotFoundException ex) {
            // removed from the JDK in later versions
        }
        Assume.assumeNotNull(handler);

        HttpURLConnection connection =
            (HttpURLConnection)new URL(null, "https://localhost:9/SoapContext/HttpsPort", handler)
                .openConnection();
        Assert.assertFalse(connection instanceof HttpsURLConnection);

        TLSClientParameters tlsClientParams = new TLSClientParameters();
        tlsClientParams.setUseHttpsURLConnectionDefaultSslSocketFactory(true);
        new HttpsURLConnectionFactory().decorateWithTLS(tlsClientParams, connection);

        Method getHostnameVerifier = connection.getClass().getMethod("getHostnameVerifier");
        Object verifier = getHostnameVerifier.invoke(connection);
        Assert.assertTrue("CXF hostname verifier was not set on the connection",
                          Proxy.isProxyClass(verifier.getClass()));

        Method verify = getHostnameVerifier.getReturnType().getMethod("verify", String.class, String.class);
        Assert.assertEquals(Boolean.FALSE, verify.invoke(verifier, "localhost", "bethal"));
        Assert.assertEquals(Boolean.TRUE, verify.invoke(verifier, "localhost", "localhost"));
    }

    private static LegacyHostnameVerifier decorateLegacyConnection(TLSClientParameters tlsClientParams)
        throws Exception {
        LegacyHttpsConnection connection =
            new LegacyHttpsConnection(new URL("https://localhost:9/SoapContext/HttpsPort"));

        new HttpsURLConnectionFactory().decorateWithTLS(tlsClientParams, connection);

        LegacyHostnameVerifier verifier = connection.getHostnameVerifier();
        Assert.assertTrue("CXF hostname verifier was not set on the connection",
                          Proxy.isProxyClass(verifier.getClass()));
        Assert.assertNotNull(connection.getSSLSocketFactory());
        return verifier;
    }

    @Test
    public void noExplicitKeystoreNoCertAlias() throws Exception {
        clearDefaults();
        System.clearProperty("javax.net.ssl.keyStore");
        System.clearProperty("javax.net.ssl.keyStorePassword");

        HttpsURLConnectionFactory factory = new HttpsURLConnectionFactory();
        Assert.assertNull(factory.socketFactory);

        TLSClientParameters tlsClientParams = new TLSClientParameters();
        tlsClientParams.setUseHttpsURLConnectionDefaultSslSocketFactory(false);

        HttpsURLConnection conn = EasyMock.createMock(HttpsURLConnection.class);

        try {
            factory.decorateWithTLS(tlsClientParams, conn);
        } catch (NullPointerException e) {
            Assert.fail("should not fail with NullPointerException");
        }
    }

    @Test
    public void noExplicitKeystoreWithCertAlias() throws Exception {
        clearDefaults();
        System.clearProperty("javax.net.ssl.keyStore");
        System.clearProperty("javax.net.ssl.keyStorePassword");

        HttpsURLConnectionFactory factory = new HttpsURLConnectionFactory();
        Assert.assertNull(factory.socketFactory);

        TLSClientParameters tlsClientParams = new TLSClientParameters();
        tlsClientParams.setUseHttpsURLConnectionDefaultSslSocketFactory(false);
        tlsClientParams.setCertAlias("someAlias");

        HttpsURLConnection conn = EasyMock.createMock(HttpsURLConnection.class);

        try {
            factory.decorateWithTLS(tlsClientParams, conn);
        } catch (NullPointerException e) {
            Assert.fail("should not fail with NullPointerException");
        }
    }

    @Test
    public void defaultKeystoreNoCertAlias() throws Exception {
        clearDefaults();
        String keystorePath = getClass().getResource("resources/defaultkeystore2").getPath();
        System.setProperty("javax.net.ssl.keyStore", keystorePath);
        System.setProperty("javax.net.ssl.keyStorePassword", "123456");

        HttpsURLConnectionFactory factory = new HttpsURLConnectionFactory();
        Assert.assertNull(factory.socketFactory);

        TLSClientParameters tlsClientParams = new TLSClientParameters();
        tlsClientParams.setUseHttpsURLConnectionDefaultSslSocketFactory(false);

        HttpsURLConnection conn = EasyMock.createMock(HttpsURLConnection.class);

        try {
            factory.decorateWithTLS(tlsClientParams, conn);
        } catch (NullPointerException e) {
            Assert.fail("should not fail with NullPointerException");
        }
    }

    @Test
    public void defaultKeystoreWithCertAlias() throws Exception {
        clearDefaults();
        String keystorePath = getClass().getResource("resources/defaultkeystore2").getPath();
        System.setProperty("javax.net.ssl.keyStore", keystorePath);
        System.setProperty("javax.net.ssl.keyStorePassword", "123456");

        HttpsURLConnectionFactory factory = new HttpsURLConnectionFactory();
        Assert.assertNull(factory.socketFactory);

        TLSClientParameters tlsClientParams = new TLSClientParameters();
        tlsClientParams.setUseHttpsURLConnectionDefaultSslSocketFactory(false);
        tlsClientParams.setCertAlias("someAlias");

        HttpsURLConnection conn = EasyMock.createMock(HttpsURLConnection.class);

        try {
            factory.decorateWithTLS(tlsClientParams, conn);
        } catch (NullPointerException e) {
            Assert.fail("should not fail with NullPointerException");
        }
    }

    private void clearDefaults() throws IllegalAccessException {
        Field defaultManagers = ReflectionUtil.getDeclaredField(SSLUtils.class, "defaultManagers");
        ReflectionUtil.setAccessible(defaultManagers);

        defaultManagers.set(SSLUtils.class, null);
    }

}
