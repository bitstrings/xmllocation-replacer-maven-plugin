package org.bitstrings.maven.plugins.xmllr;

public class FilterProperty
{
    protected String name;
    protected String value;
    protected Boolean pathValueToUri;
    protected Boolean valueIsDirectory;

    public String getName()
    {
        return name;
    }

    public void setName( String name )
    {
        this.name = name;
    }

    public String getValue()
    {
        return value;
    }

    public void setValue( String value )
    {
        this.value = value;
    }

    public Boolean getPathValueToUri()
    {
        return pathValueToUri;
    }

    public void setPathValueToUri( Boolean pathValueToUri )
    {
        this.pathValueToUri = pathValueToUri;
    }

    public Boolean getValueIsDirectory()
    {
        return valueIsDirectory;
    }

    public void setValueIsDirectory( Boolean valueIsDirectory )
    {
        this.valueIsDirectory = valueIsDirectory;
    }
}
