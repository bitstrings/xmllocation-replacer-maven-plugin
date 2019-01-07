package org.bitstrings.maven.plugins.xmllr;

import static java.lang.String.join;
import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections4.IteratorUtils.chainedIterator;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_RESOURCES;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.FileSet;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
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
    protected MavenProject mavenProject;

    @Parameter( defaultValue = "${session}", readonly = true )
    protected MavenSession mavenSession;

    @Component
    protected BuildContext buildContext;

    @Component
    private MavenResourcesFiltering mavenResourcesFiltering;

    @Parameter( required = true )
    protected File catalogFile;

    @Parameter( defaultValue = "false", required = false )
    protected Boolean copyCatalogFile;

    @Parameter( defaultValue = "${project.build.directory}", required = false )
    protected String outputCatalogDirectory;

    @Parameter( defaultValue = "${project.build.filters}", readonly = true )
    protected List<File> catalogFilters;

    @Parameter( defaultValue = "false" )
    protected Boolean catalogFiltering;

    @Parameter( defaultValue = "false" )
    protected Boolean catalogProjectFilters;

    @Parameter( required = true, readonly = true )
    protected List<FileSet> xmlFileSets;

    @Parameter( defaultValue = "${project.build.directory}", required = false )
    protected File outputDirectory;

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        File outputCatalogDirectoryFile = null;

        File catalogFilteredFile = null;

        try
        {
            boolean catalogChanged = buildContext.hasDelta( catalogFile );

            catalogFilteredFile = Files.createTempDirectory( "xmllr-" ).toFile();

            catalogFilteredFile.mkdirs();

            catalogFilteredFile = new File( catalogFilteredFile, catalogFile.getName() );

            boolean catalogFiltersChanged = false;

            List<String> catalogFiltersCombined = new ArrayList<>( catalogFilters.size() );

            for ( File catalogFilter : catalogFilters )
            {
                if ( buildContext.hasDelta( catalogFilter ) )
                {
                    catalogFiltersChanged = true;
                }

                catalogFiltersCombined.add( makeItAbsolute( catalogFilter, null ).getAbsolutePath() );
            }

            if ( catalogFiltering )
            {
                if ( catalogChanged || catalogFiltersChanged )
                {
                    Resource resource = new Resource();
                    resource.setDirectory( catalogFile.getParent() );
                    resource.setFiltering( true );
                    resource.addInclude( catalogFile.getName() );
    //                resource.setTargetPath( catalogFilteredFile.getParentFile().getAbsolutePath() );

                    MavenResourcesExecution resourcesExecution =
                        new MavenResourcesExecution(
                            singletonList( resource ),
                            catalogFilteredFile.getParentFile(),
                            mavenProject,
                            "UTF-8",
                            catalogFiltersCombined,
                            EMPTY_LIST,
                            mavenSession );

                    resourcesExecution.setEscapeWindowsPaths( true );
                    resourcesExecution.setInjectProjectBuildFilters( catalogProjectFilters );
                    resourcesExecution.setOverwrite( true );
                    resourcesExecution.setIncludeEmptyDirs( false );
                    resourcesExecution.setSupportMultiLineFiltering( false );

                    mavenResourcesFiltering.filterResources( resourcesExecution );
                }
            }

            if ( catalogChanged )
            {
                if ( copyCatalogFile && ( outputCatalogDirectory != null ) )
                {
                    outputCatalogDirectoryFile = new File( outputCatalogDirectory );

                    outputCatalogDirectoryFile =
                        makeItAbsolute(
                            outputCatalogDirectoryFile,
                            new File( mavenProject.getBuild().getDirectory() ) );

                    outputCatalogDirectoryFile.mkdirs();

                    FileUtils.copyFile(
                        catalogFiltering
                            ? catalogFilteredFile
                            : catalogFile,
                        new File( outputCatalogDirectoryFile, catalogFile.getName() ) );
                }
            }

            //if ( catalogFiltering ) throw new Exception ( "" );

            CatalogManager catalogManager = new CatalogManager();
            catalogManager.setVerbosity( 0 );
            catalogManager.setAllowOasisXMLCatalogPI( true );
            catalogManager.setIgnoreMissingProperties( true );
            catalogManager.setUseStaticCatalog( false );
            catalogManager.setCatalogFiles( catalogFilteredFile.toString() );

            Catalog catalog = catalogManager.getCatalog();

            for ( FileSet xmlFileSet : xmlFileSets )
            {
                File fileSetDirectoryFile = makeItAbsolute( new File( xmlFileSet.getDirectory() ), null );

                xmlFileSet.setDirectory( fileSetDirectoryFile.getAbsolutePath() );

                for ( File file :
                        FileUtils.getFiles(
                            fileSetDirectoryFile,
                            join( ",", xmlFileSet.getIncludes() ),
                            join( ",", xmlFileSet.getExcludes() ) ) )
                {
                    if ( !buildContext.hasDelta( file ) && !catalogChanged )
                    {
                        continue;
                    }

                    File parentFile =
                        makeItAbsolute(
                            new File( outputDirectory, relativize( fileSetDirectoryFile, file.getParentFile() ) ),
                            new File( mavenProject.getBuild().getDirectory() ) );

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
        finally
        {
            if ( catalogFilteredFile != null )
            {
                catalogFilteredFile.delete();
                catalogFilteredFile.getParentFile().delete();
            }
        }
    }

    protected String relativize( File basedir, File dir )
    {
        return
            Paths.get( basedir.toURI() )
                .relativize( Paths.get( dir.toURI() ) )
                .toFile()
                .toString();
    }

    protected File makeItAbsolute( File file, File basedirOnRelative )
    {
        if ( !file.isAbsolute() )
        {
            file =
                new File(
                    basedirOnRelative == null
                        ? mavenProject.getBasedir()
                        : basedirOnRelative, file.getPath() );
        }

        return file;
    }
}
