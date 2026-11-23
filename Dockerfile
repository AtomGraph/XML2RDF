# Saxon-HE with the stylesheet, called as `docker run -i atomgraph/xml2rdf` with the XML on standard input and the RDF, as
# RDF/XML, on standard output. The jar's command line writes N-Triples instead; both are the same triples.

FROM atomgraph/saxon:12.9

LABEL maintainer="martynas@atomgraph.com"

COPY src/main/resources/com/atomgraph/etl/xml/xml2rdf.xsl /usr/local/share/xml2rdf/xml2rdf.xsl

CMD ["-s:-", "-xsl:/usr/local/share/xml2rdf/xml2rdf.xsl"]
