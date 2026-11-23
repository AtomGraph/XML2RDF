# XML2RDF
Generic XML to RDF converter

Reads arbitrary XML and writes generic RDF: no schema, no mapping, no configuration. Elements and attributes become
properties under their local names, nesting becomes blank nodes. The conversion is an XSLT 3.0 stylesheet,
[`xml2rdf.xsl`](src/main/resources/com/atomgraph/etl/xml/xml2rdf.xsl), run with Saxon-HE.

As with [CSV2RDF](https://github.com/AtomGraph/CSV2RDF) and [JSON2RDF](https://github.com/AtomGraph/JSON2RDF), the common use case is
feeding the output into a triplestore or SPARQL processor and using a SPARQL `CONSTRUCT` query to map the generic RDF to RDF that uses terms
from some vocabulary.

## Conversion

* an element with attributes or child elements is a blank node
* a child element holding only text is a literal of its parent, under the child's local name
* a child element with attributes or children of its own is a blank node of its parent, under its local name
* an attribute is a literal under its local name (`xml:*` and `xsi:*` attributes are left out)
* text beside child elements is `rdf:value`
* an empty element with no attributes gives nothing

Every property is in the namespace `https://w3id.org/atomgraph/xml2rdf#`, except `rdf:value`. Names are taken as the document spells them,
without their namespaces. Values are plain strings, as the document holds them. A repeated element is a repeated property, its order lost.

## Build

    mvn clean install

That should produce an executable JAR file `target/xml2rdf-<version>-jar-with-dependencies.jar` in which dependency libraries will be included.

## Maven

Each version is released to the Maven central repository as [`com.atomgraph.etl.xml/xml2rdf`](https://central.sonatype.com/artifact/com.atomgraph.etl.xml/xml2rdf).
The stylesheet is in the jar as `/com/atomgraph/etl/xml/xml2rdf.xsl`, for running it with an XSLT processor of your own.

## Usage

The XML data is read from `stdin`, the resulting RDF data is written to `stdout`.

XML2RDF is available as a `.jar` as well as a Docker image [atomgraph/xml2rdf](https://hub.docker.com/r/atomgraph/xml2rdf) (recommended).
The two write the same triples in different syntaxes:

* the jar writes N-Triples
* the image, which is [atomgraph/saxon](https://hub.docker.com/r/atomgraph/saxon) with the stylesheet, writes the stylesheet's RDF/XML

The image is built for `linux/amd64` and `linux/arm64`.

It takes no parameters: the output has no URIs other than the properties', so it needs no base URI.

## Example

XML data in `staff.xml`:

```xml
<staff xmlns="http://example.org/hr">
  <employee id="E1007">
    <name>Ada Byron</name>
    <department code="RND">Research</department>
    <phone kind="Mobile">+44 7700 900018</phone>
    <phone kind="Work">+44 20 7946 0000</phone>
    <note/>
  </employee>
</staff>
```

Java execution from shell:

```bash
cat staff.xml | java -jar xml2rdf-jar-with-dependencies.jar | riot --formatted=TURTLE
```

Alternatively, Docker execution from shell:

```bash
cat staff.xml | docker run --rm -i -a stdin -a stdout -a stderr atomgraph/xml2rdf | riot --syntax=RDF/XML --formatted=TURTLE
```

Note that using Docker you need to [bind](https://docs.docker.com/engine/reference/commandline/run/#attach-to-stdinstdoutstderr--a) `stdin`/`stdout`/`stderr` streams.

Turtle output

```turtle
@prefix x:   <https://w3id.org/atomgraph/xml2rdf#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .

[ x:employee [ x:id         "E1007" ;
               x:name       "Ada Byron" ;
               x:department [ x:code "RND" ; rdf:value "Research" ] ;
               x:phone      [ x:kind "Mobile" ; rdf:value "+44 7700 900018" ] ,
                            [ x:kind "Work" ; rdf:value "+44 20 7946 0000" ]
             ]
] .
```

The following SPARQL query maps this generic RDF to [schema.org](https://schema.org):

```sparql
PREFIX rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX x:      <https://w3id.org/atomgraph/xml2rdf#>
PREFIX schema: <https://schema.org/>

CONSTRUCT
{
    ?person a schema:Person ;
        schema:name ?name ;
        schema:worksFor ?department .
    ?department a schema:Organization ;
        schema:name ?departmentName .
    ?person schema:contactPoint [ schema:contactType ?kind ; schema:telephone ?phone ] .
}
WHERE
{
    ?employee x:id ?id ;
        x:name ?name .
    BIND (IRI(CONCAT("https://hr.example/employees/", ?id)) AS ?person)
    OPTIONAL
    {
        ?employee x:department [ x:code ?code ; rdf:value ?departmentName ] .
        BIND (IRI(CONCAT("https://hr.example/departments/", ?code)) AS ?department)
    }
    OPTIONAL { ?employee x:phone [ x:kind ?kind ; rdf:value ?phone ] }
}
```

[Jena's `sparql`](https://jena.apache.org/documentation/query/cmds.html) runs it over the output:

```bash
cat staff.xml | java -jar xml2rdf-jar-with-dependencies.jar > staff.nt
sparql --data=staff.nt --query=staff.rq
```

## Limitations

XML2RDF does not stream: Saxon-HE builds the whole document in memory before the stylesheet runs, so the input must fit in the heap. The output is
streamed as it is produced.

## Dependencies

* [Saxon-HE](https://www.saxonica.com/products/products.xml)
* [Apache Jena](https://jena.apache.org/)
* [picocli](https://picocli.info)
