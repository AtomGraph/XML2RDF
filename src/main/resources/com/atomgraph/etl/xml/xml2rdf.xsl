<?xml version="1.0" encoding="UTF-8"?>
<!--
  Copyright 2026 Martynas Jusevičius <martynas@atomgraph.com>

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
<!--
  XML to generic RDF, as CSV2RDF and JSON2RDF do for their formats: triples a SPARQL CONSTRUCT then maps to a
  vocabulary. Each element with attributes or child elements is a blank node; a child element holding only text is a
  literal of its parent, under the child's local name; one with attributes or children of its own is a blank node
  of its parent, under its name; an attribute is a literal under its local name; text beside child elements is
  rdf:value. Every property is in the xml2rdf namespace, the names as the document spells them, without their
  namespaces. Values are strings, as the document holds them; an empty element with no attributes gives nothing, as an
  empty CSV cell does. A repeated element is a repeated property, its order lost.

  <staff><employee id="E1007"><name>Ada Byron</name><phone kind="Mobile">+44 7700 900018</phone></employee></staff>
  gives
  _:s x:employee _:e . _:e x:id "E1007" ; x:name "Ada Byron" ; x:phone _:p . _:p x:kind "Mobile" ; rdf:value "+44 7700 900018" .
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    xmlns:x="https://w3id.org/atomgraph/xml2rdf#"
    exclude-result-prefixes="#all">

    <xsl:output method="xml" encoding="UTF-8" indent="no"/>

    <xsl:variable name="x" select="'https://w3id.org/atomgraph/xml2rdf#'"/>

    <xsl:template match="/">
        <rdf:RDF>
            <xsl:apply-templates select="*" mode="node"/>
        </rdf:RDF>
    </xsl:template>

    <!-- an element that is a node: its attributes, its children, and its own text when it has children or attributes -->
    <xsl:template match="*" mode="node">
        <rdf:Description>
            <xsl:apply-templates select="@*[not(namespace-uri() = ('http://www.w3.org/XML/1998/namespace', 'http://www.w3.org/2001/XMLSchema-instance'))]"/>
            <xsl:apply-templates select="*" mode="property"/>
            <xsl:if test="normalize-space(string-join(text(), ''))">
                <rdf:value><xsl:value-of select="string-join(text(), '')"/></rdf:value>
            </xsl:if>
        </rdf:Description>
    </xsl:template>

    <xsl:template match="@*">
        <xsl:element name="x:{local-name()}" namespace="{$x}"><xsl:value-of select="."/></xsl:element>
    </xsl:template>

    <!-- a child element: a literal when it holds only text, a node of its own otherwise, nothing when it holds nothing -->
    <xsl:template match="*" mode="property">
        <xsl:variable name="attributes" select="@*[not(namespace-uri() = ('http://www.w3.org/XML/1998/namespace', 'http://www.w3.org/2001/XMLSchema-instance'))]"/>
        <xsl:choose>
            <xsl:when test="* or $attributes">
                <xsl:element name="x:{local-name()}" namespace="{$x}">
                    <xsl:apply-templates select="." mode="node"/>
                </xsl:element>
            </xsl:when>
            <xsl:when test="normalize-space(.)">
                <xsl:element name="x:{local-name()}" namespace="{$x}"><xsl:value-of select="."/></xsl:element>
            </xsl:when>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
