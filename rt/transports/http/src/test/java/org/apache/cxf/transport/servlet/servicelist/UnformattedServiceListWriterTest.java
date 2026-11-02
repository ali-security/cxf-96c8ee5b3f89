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

import org.apache.cxf.Bus;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractDestination;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

public class UnformattedServiceListWriterTest extends Assert {

    private static final String MALICIOUS_BASE_PATH =
        "http://localhost:8080/services/<script>alert(1)</script>\"onmouseover=\"alert(2)";

    private static final String ESCAPED_BASE_PATH =
        "http://localhost:8080/services/&lt;script&gt;alert(1)&lt;/script&gt;&quot;onmouseover=&quot;alert(2)";

    private static final AbstractDestination[] NONE = new AbstractDestination[0];

    @Test
    public void testEscapeHTMLSoapEndpointAddress() throws Exception {
        AbstractDestination soap = createDestination("/greeter");
        EasyMock.replay(soap);

        String result = writeServiceList(new UnformattedServiceListWriter(false, null), MALICIOUS_BASE_PATH,
                                         new AbstractDestination[] {soap}, NONE);

        assertNoInjectedMarkup(result);
        assertTrue(result, result.contains(ESCAPED_BASE_PATH + "/greeter\n"));
    }

    @Test
    public void testEscapeHTMLSoapEndpointAddressWsdlList() throws Exception {
        AbstractDestination soap = createDestination("/greeter");
        EasyMock.replay(soap);

        String result = writeServiceList(new UnformattedServiceListWriter(true, null), MALICIOUS_BASE_PATH,
                                         new AbstractDestination[] {soap}, NONE);

        assertNoInjectedMarkup(result);
        assertTrue(result, result.contains(ESCAPED_BASE_PATH + "/greeter?wsdl\n"));
    }

    @Test
    public void testEscapeHTMLRestfulEndpointAddress() throws Exception {
        AbstractDestination rest = createDestination("/books");
        EasyMock.replay(rest);

        String result = writeServiceList(new UnformattedServiceListWriter(false, null), MALICIOUS_BASE_PATH,
                                         NONE, new AbstractDestination[] {rest});

        assertNoInjectedMarkup(result);
        assertTrue(result, result.contains(ESCAPED_BASE_PATH + "/books\n"));
    }

    @Test
    public void testEscapeHTMLRestfulEndpointAddressWadlAndSwagger() throws Exception {
        AbstractDestination rest = createDestination("/books");
        Bus bus = EasyMock.createNiceMock(Bus.class);
        EasyMock.expect(bus.getProperty("wadl.service.descrition.available")).andReturn("true").anyTimes();
        EasyMock.expect(bus.getProperty("swagger.service.descrition.available")).andReturn("true").anyTimes();
        EasyMock.replay(rest, bus);

        String result = writeServiceList(new UnformattedServiceListWriter(false, bus), MALICIOUS_BASE_PATH,
                                         NONE, new AbstractDestination[] {rest});

        assertNoInjectedMarkup(result);
        assertTrue(result, result.contains(ESCAPED_BASE_PATH + "/books?_wadl\n"));
        assertTrue(result, result.contains(ESCAPED_BASE_PATH + "/books/swagger.json\n"));
    }

    @Test
    public void testBenignEndpointAddressUnchanged() throws Exception {
        AbstractDestination soap = createDestination("/greeter");
        AbstractDestination rest = createDestination("/books");
        EasyMock.replay(soap, rest);

        String basePath = "http://localhost:8080/services";
        String result = writeServiceList(new UnformattedServiceListWriter(false, null), basePath,
                                         new AbstractDestination[] {soap},
                                         new AbstractDestination[] {rest});

        assertEquals(basePath + "/greeter\n\n" + basePath + "/books\n", result);
    }

    private static void assertNoInjectedMarkup(String result) {
        assertFalse(result, result.contains("<script>"));
        assertFalse(result, result.contains("</script>"));
        assertFalse(result, result.contains("\"onmouseover"));
        assertFalse(result, result.contains("onmouseover=\""));
    }

    private static String writeServiceList(UnformattedServiceListWriter writer,
                                           String basePath,
                                           AbstractDestination[] soapDestinations,
                                           AbstractDestination[] restDestinations) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        writer.writeServiceList(pw, basePath, soapDestinations, restDestinations);
        pw.flush();
        return sw.toString();
    }

    private static AbstractDestination createDestination(String address) {
        EndpointInfo ei = new EndpointInfo();
        ei.setAddress(address);
        AbstractDestination d = EasyMock.createMock(AbstractDestination.class);
        EasyMock.expect(d.getEndpointInfo()).andReturn(ei).anyTimes();
        return d;
    }
}
