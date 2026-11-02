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
package org.apache.cxf.transport.commons_text;

import org.junit.Assert;
import org.junit.Test;

public class StringEscapeUtilsTest extends Assert {

    @Test
    public void testEscapeHtml4Null() {
        assertNull(StringEscapeUtils.escapeHtml4(null));
    }

    @Test
    public void testEscapeHtml4NoSpecialCharacters() {
        String url = "http://localhost:8080/services/greeter?wsdl";
        assertEquals(url, StringEscapeUtils.escapeHtml4(url));
        assertEquals("", StringEscapeUtils.escapeHtml4(""));
    }

    @Test
    public void testEscapeHtml4BasicCharacters() {
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;",
                     StringEscapeUtils.escapeHtml4("<script>alert(1)</script>"));
        assertEquals("&quot;bread&quot; &amp; &quot;butter&quot;",
                     StringEscapeUtils.escapeHtml4("\"bread\" & \"butter\""));
        assertEquals("&lt;a href=&quot;x&quot; onmouseover=&quot;alert(1)&quot;&gt;",
                     StringEscapeUtils.escapeHtml4("<a href=\"x\" onmouseover=\"alert(1)\">"));
    }

    @Test
    public void testEscapeHtml4ExtendedCharacters() {
        assertEquals("&nbsp;&eacute;&euro;", StringEscapeUtils.escapeHtml4(" é€"));
    }
}
