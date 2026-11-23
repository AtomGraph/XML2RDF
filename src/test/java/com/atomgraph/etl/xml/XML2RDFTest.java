/*
 * Copyright 2026 Martynas Jusevičius <martynas@atomgraph.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.atomgraph.etl.xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import net.sf.saxon.s9api.SaxonApiException;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.RDF;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Converts <code>staff.xml</code> and checks the generic triples.
 *
 * @author Martynas Jusevičius {@literal <martynas@atomgraph.com>}
 */
public class XML2RDFTest
{

    static Model generic;

    @BeforeAll
    static void convert() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream xml = XML2RDFTest.class.getResourceAsStream("/staff.xml"))
        {
            new XML2RDF(xml, out).convert();
        }
        generic = ModelFactory.createDefaultModel();
        RDFParser.fromString(out.toString(StandardCharsets.UTF_8), Lang.NTRIPLES).parse(generic);
    }

    private static Property x(String name)
    {
        return ResourceFactory.createProperty(XML2RDF.NS, name);
    }

    private static Resource employee(String id)
    {
        return generic.listSubjectsWithProperty(x("id"), id).next();
    }

    private static Set<String> literals(Resource subject, Property p)
    {
        return subject.listProperties(p).mapWith(s -> s.getLiteral().getLexicalForm()).toSet();
    }

    @Test
    public void elementsAreNodesTextIsLiteralsUnderTheirLocalNames()
    {
        Resource staff = generic.listSubjectsWithProperty(x("employee")).next();
        assertTrue(staff.isAnon());
        assertEquals(2, staff.listProperties(x("employee")).toList().size(), "a repeated element is a repeated property");
        Resource ada = employee("E1007");
        assertEquals(Set.of("Ada Byron"), literals(ada, x("name")), "a text-only child is a literal, its namespace dropped");
        assertEquals(Set.of("SPARQL", "XSLT"), literals(ada, x("skill")));
        assertFalse(ada.hasProperty(x("note")), "an empty element gives nothing");
        Resource department = ada.getPropertyResourceValue(x("department"));
        assertEquals(Set.of("RND"), literals(department, x("code")), "an attribute is a literal under its name");
        assertEquals(Set.of("Research"), literals(department, RDF.value), "an element with attributes is a node, its text rdf:value");
        assertEquals(Set.of("Mobile", "Work"), ada.listProperties(x("phone")).mapWith(s -> s.getResource().getProperty(x("kind")).getString()).toSet());
        assertEquals(Set.of("+44 20 7946 0001"), literals(employee("E1001"), x("phone")), "the same element without attributes is a literal");
        assertTrue(generic.listStatements().toList().stream().allMatch(s -> s.getPredicate().getNameSpace().equals(XML2RDF.NS) || s.getPredicate().equals(RDF.value)),
            "every property in the xml2rdf namespace, or rdf:value");
    }

    @Test
    public void aConstructMapsTheGenericTriples()
    {
        String query = """
            PREFIX x:      <https://w3id.org/atomgraph/xml2rdf#>
            PREFIX schema: <https://schema.org/>
            CONSTRUCT { ?person a schema:Person ; schema:name ?name }
            WHERE { ?employee x:id ?id ; x:name ?name BIND (IRI(CONCAT("https://hr.example/employees/", ?id)) AS ?person) }
            """;
        Model mapped;
        try (QueryExecution exec = QueryExecution.model(generic).query(query).build())
        {
            mapped = exec.execConstruct();
        }
        Property name = mapped.createProperty("https://schema.org/name");
        assertEquals("Ada Byron", mapped.getResource("https://hr.example/employees/E1007").getProperty(name).getString());
        assertEquals("Charles Babbage", mapped.getResource("https://hr.example/employees/E1001").getProperty(name).getString());
    }

    @Test
    public void malformedXmlFailsWithTheParsersError()
    {
        InputStream xml = new ByteArrayInputStream("<staff><employee></staff>".getBytes(StandardCharsets.UTF_8));
        assertThrows(SaxonApiException.class, () -> new XML2RDF(xml, new ByteArrayOutputStream()).convert());
    }

}
