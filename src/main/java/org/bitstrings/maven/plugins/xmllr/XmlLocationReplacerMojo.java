package org.bitstrings.maven.plugins.xmllr;

import static java.lang.String.join;
import static org.apache.commons.collections4.IteratorUtils.chainedIterator;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_RESOURCES;

import java.io.File;
import java.nio.file.Paths;
import java.util.Iterator;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.FileUtils;
import org.apache.xml.resolver.Catalog;
import org.apache.xml.resolver.CatalogManager;
import org.eaxy.Document;
import org.eaxy.Element;
import org.eaxy.Xml;
import org.sonatype.plexus.build.incremental.BuildContext;

@Mojo( name = "replace", defaultPhase = GENERATE_RESOURCES, threadSafe = true )
public class XmlLocationReplacerMojo
    extends AbstractMojo
{
    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject mavenProject;

    @Parameter( defaultValue = "${session}", readonly = true )
    private MavenSession mavenSession;

    @Component
    private BuildContext buildContext;

    @Parameter( required = true )
    private File catalogFile;

    @Parameter( required = true )
    private FileSet[] xmlFileSets;

    @Parameter( required = false, defaultValue = "${project.build.directory}" )
    private File outputDirectory;

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        try
        {
            CatalogManager catalogManager = new CatalogManager();
            catalogManager.setVerbosity( 0 );
            catalogManager.setAllowOasisXMLCatalogPI( true );
            catalogManager.setIgnoreMissingProperties( true );
            catalogManager.setUseStaticCatalog( false );
            catalogManager.setCatalogFiles( catalogFile.toString() );

            Catalog catalog = catalogManager.getCatalog();

            boolean catalogChanged = buildContext.hasDelta( catalogFile );

            for ( FileSet xmlFileSet : xmlFileSets )
            {
                for ( File file :
                        FileUtils.getFiles(
                            new File( xmlFileSet.getDirectory() ),
                            join( ",", xmlFileSet.getIncludes() ),
                            join( ",", xmlFileSet.getExcludes() ) ) )
                {
                    if ( !buildContext.hasDelta( file ) && !catalogChanged )
                    {
                        continue;
                    }

                    File parentFile =
                        new File(
                            outputDirectory,
                            Paths.get(
                                xmlFileSet.getDirectory() ).relativize( Paths.get( file.getParentFile().toURI() )
                            ).toFile().toString()
                        );

                    parentFile.mkdirs();

                    Document doc = Xml.read( file );

                    for (
                        Iterator<Element> iter =
                            chainedIterator(
                                doc.find( "import" ).elements().iterator(),
                                doc.find( "include" ).elements().iterator() ) ;
                        iter.hasNext() ; )
                    {
                        Element elem = iter.next();

                        String currentLocation = elem.attr( "schemaLocation" );
                        elem.attr( "schemaLocation", catalog.resolvePublic( currentLocation, currentLocation ) );
                    }

                    File outFile = new File( parentFile, file.getName() );

                    FileUtils.fileWrite( outFile, doc.getEncoding(), doc.toXML() );

                    buildContext.refresh( outFile );
                }
            }
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( e.getLocalizedMessage(), e );
        }
    }
}
