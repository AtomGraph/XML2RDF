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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.system.StreamRDFLib;
import picocli.CommandLine;

/**
 * XML2RDF conversion utility.
 * Runs the generic <code>xml2rdf.xsl</code> stylesheet over the XML and writes its triples as N-Triples.
 *
 * @author Martynas Jusevičius {@literal <martynas@atomgraph.com>}
 */
@CommandLine.Command(name = "xml2rdf",
    description = "Reads XML from standard input and writes generic RDF as N-Triples to standard output")
public class XML2RDF implements Callable<Integer>
{

    /** Namespace of the generic properties */
    public static final String NS = "https://w3id.org/atomgraph/xml2rdf#";
    /** Classpath location of the stylesheet */
    public static final String STYLESHEET = "/com/atomgraph/etl/xml/xml2rdf.xsl";

    private final InputStream xmlIn;
    private final OutputStream rdfOut;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Show this help message and exit")
    private boolean help;

    /**
     * Entrypoint to the program.
     *
     * @param args command line arguments
     */
    public static void main(String[] args)
    {
        System.exit(new CommandLine(new XML2RDF(System.in, System.out)).execute(args));
    }

    /**
     * Constructs converter from XML input stream and RDF output stream.
     *
     * @param xmlIn XML input stream
     * @param rdfOut RDF output stream
     */
    public XML2RDF(InputStream xmlIn, OutputStream rdfOut)
    {
        this.xmlIn = xmlIn;
        this.rdfOut = rdfOut;
    }

    @Override
    public Integer call() throws IOException, SaxonApiException
    {
        convert();
        return 0;
    }

    /**
     * Compiles the generic stylesheet.
     *
     * @param processor Saxon processor
     * @return compiled stylesheet
     */
    public static XsltExecutable compile(Processor processor)
    {
        try (InputStream xsl = XML2RDF.class.getResourceAsStream(STYLESHEET))
        {
            return processor.newXsltCompiler().compile(new StreamSource(xsl, XML2RDF.class.getResource(STYLESHEET).toString()));
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
        catch (SaxonApiException ex)
        {
            throw new IllegalStateException("The xml2rdf stylesheet does not compile", ex);
        }
    }

    /**
     * Converts the XML to N-Triples.
     * The stylesheet's RDF/XML is piped into the RDF/XML parser as it is serialised, never held in full.
     *
     * @throws IOException I/O error
     * @throws SaxonApiException transformation error, such as XML that is not well-formed
     */
    public void convert() throws IOException, SaxonApiException
    {
        Processor processor = new Processor(false);
        XsltTransformer transformer = compile(processor).load();
        transformer.setSource(new StreamSource(xmlIn));

        AtomicReference<SaxonApiException> failure = new AtomicReference<>();
        try (PipedInputStream rdfXmlIn = new PipedInputStream(1 << 16))
        {
            PipedOutputStream rdfXmlOut = new PipedOutputStream(rdfXmlIn);
            Thread transform = new Thread(() ->
            {
                try (rdfXmlOut)
                {
                    transformer.setDestination(processor.newSerializer(rdfXmlOut));
                    transformer.transform();
                }
                catch (SaxonApiException ex)
                {
                    failure.set(ex);
                }
                catch (IOException ex)
                {
                    throw new UncheckedIOException(ex);
                }
            }, "xml2rdf-transform");
            transform.start();

            StreamRDF rdfStream = StreamRDFLib.writer(rdfOut);
            rdfStream.start();
            try
            {
                RDFParser.source(rdfXmlIn).lang(Lang.RDFXML).errorHandler(ErrorHandlerFactory.errorHandlerNoLogging).parse(rdfStream);
            }
            catch (RuntimeException ex)
            { // the stylesheet's failure, if any, is why its output could not be parsed
                join(transform);
                if (failure.get() != null) throw failure.get();
                throw ex;
            }
            finally
            {
                rdfStream.finish();
            }

            join(transform);
        }

        if (failure.get() != null) throw failure.get();
    }

    private static void join(Thread thread) throws IOException
    {
        try
        {
            thread.join();
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while transforming", ex);
        }
    }

}
