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
package org.apache.cxf.transport.servlet.servicelist;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;

import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.service.model.InterfaceInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.transport.AbstractDestination;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

public class FormattedServiceListWriterTest extends Assert {

    private static final String NS = "http://apache.org/hello_world";

    private static final String MALICIOUS_BASE_PATH =
        "http://localhost:8080/services/<script>alert(1)</script>\"onmouseover=\"alert(2)";

    private static final String ESCAPED_BASE_PATH =
        "http://localhost:8080/services/&lt;script&gt;alert(1)&lt;/script&gt;&quot;onmouseover=&quot;alert(2)";

    private static final AbstractDestination[] NONE = new AbstractDestination[0];

    @Test
    public void testEscapeHTMLSoapEndpointAddress() throws Exception {
        AbstractDestination soap = createSoapDestination("/greeter");
        EasyMock.replay(soap);

        String result = writeServiceList(null, MALICIOUS_BASE_PATH,
                                         new AbstractDestination[] {soap}, NONE);

        assertNoInjectedMarkup(result);
        assertTrue(result, result.contains("<span class=\"value\">" + ESCAPED_BASE_PATH + "/greeter</span>"));
        assertTrue(result, result.contains("<a href=\"" + ESCAPED_BASE_PATH + "/greeter?wsdl\">"));
        EasyMock.verify(soap);
    }

    @Test
    public void testEscapeHTMLRestfulEndpointAddress() throws Exception {
        AbstractDestination rest = createRestDestination("/books");
        Bus bus = EasyMock.createNiceMock(Bus.class);
        EasyMock.expect(bus.getProperty("org.apache.cxf.extensions.logging.atom.pull"))
            .andReturn(Collections.singletonMap("/books", "/books/logs")).anyTimes();
        EasyMock.expect(bus.getProperty("wadl.service.description.available")).andReturn("true").anyTimes();
        EasyMock.expect(bus.getProperty("swagger.service.description.available"))
            .andReturn("true").anyTimes();
        EasyMock.replay(rest, bus);

        String result = writeServiceList(bus, MALICIOUS_BASE_PATH, NONE, new AbstractDestination[] {rest});

        assertNoInjectedMarkup(result);
        assertTrue(result, result.contains("<span class=\"value\">" + ESCAPED_BASE_PATH + "/books</span>"));
        // WADL link
        assertTrue(result, result.contains("<a href=\"" + ESCAPED_BASE_PATH + "/books?_wadl\">"));
        // Swagger link
        assertTrue(result, result.contains("<a href=\"" + ESCAPED_BASE_PATH + "/books/swagger.json\">"));
        // Atom log feed link
        assertTrue(result, result.contains("Atom Log Feed"));
        EasyMock.verify(rest);
    }

    @Test
    public void testEscapeHTMLRestfulEndpointAddressWithSwaggerUi() throws Exception {
        AbstractDestination rest = createRestDestination("/books");
        Bus bus = EasyMock.createNiceMock(Bus.class);
        EasyMock.expect(bus.getProperty("swagger.service.description.available"))
            .andReturn("true").anyTimes();
        EasyMock.expect(bus.getProperty("swagger.service.ui.available")).andReturn("true").anyTimes();
        EasyMock.replay(rest, bus);

        String result = writeServiceList(bus, MALICIOUS_BASE_PATH, NONE, new AbstractDestination[] {rest});

        assertNoInjectedMarkup(result);
        assertTrue(result, result.contains("<span class=\"value\">" + ESCAPED_BASE_PATH + "/books</span>"));
        assertTrue(result, result.contains("Swagger :"));
        EasyMock.verify(rest);
    }

    @Test
    public void testBenignEndpointAddressUnchanged() throws Exception {
        AbstractDestination soap = createSoapDestination("/greeter");
        AbstractDestination rest = createRestDestination("/books");
        EasyMock.replay(soap, rest);

        String basePath = "http://localhost:8080/services";
        String result = writeServiceList(null, basePath,
                                         new AbstractDestination[] {soap},
                                         new AbstractDestination[] {rest});

        assertTrue(result, result.contains("<span class=\"value\">" + basePath + "/greeter</span>"));
        assertTrue(result, result.contains("<a href=\"" + basePath + "/greeter?wsdl\">"));
        assertTrue(result, result.contains("<span class=\"value\">" + basePath + "/books</span>"));
    }

    private static void assertNoInjectedMarkup(String result) {
        assertFalse(result, result.contains("<script>"));
        assertFalse(result, result.contains("</script>"));
        assertFalse(result, result.contains("\"onmouseover"));
        assertFalse(result, result.contains("onmouseover=\""));
    }

    private static String writeServiceList(Bus bus,
                                           String basePath,
                                           AbstractDestination[] soapDestinations,
                                           AbstractDestination[] restDestinations) throws Exception {
        FormattedServiceListWriter writer =
            new FormattedServiceListWriter("/services/?stylesheet=1", "CXF - Service list", true, bus);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        writer.writeServiceList(pw, basePath, soapDestinations, restDestinations);
        pw.flush();
        return sw.toString();
    }

    private static AbstractDestination createSoapDestination(String address) {
        ServiceInfo si = new ServiceInfo();
        si.setName(new QName(NS, "SOAPService"));
        si.setTargetNamespace(NS);
        InterfaceInfo ii = new InterfaceInfo(si, new QName(NS, "Greeter"));
        ii.addOperation(new QName(NS, "greetMe"));
        EndpointInfo ei = new EndpointInfo(si, "http://schemas.xmlsoap.org/soap/http");
        ei.setName(new QName(NS, "SoapPort"));
        ei.setAddress(address);
        return createDestination(ei);
    }

    private static AbstractDestination createRestDestination(String address) {
        EndpointInfo ei = new EndpointInfo();
        ei.setAddress(address);
        return createDestination(ei);
    }

    private static AbstractDestination createDestination(EndpointInfo ei) {
        AbstractDestination d = EasyMock.createMock(AbstractDestination.class);
        EasyMock.expect(d.getEndpointInfo()).andReturn(ei).anyTimes();
        return d;
    }
}
