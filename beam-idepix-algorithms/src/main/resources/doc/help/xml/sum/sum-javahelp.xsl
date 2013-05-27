<?xml version='1.0'?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version="1.0">

    <!--
    <xsl:import href="http://docbook.sourceforge.net/release/xsl/1.73.2/javahelp/javahelp.xsl"/>
    -->
    <xsl:import href="../../docbook-xsl/1.73.2/javahelp/javahelp.xsl"/>

    <!-- Stylesheet Extensions -->
    <xsl:variable name="use.extensions" select="1"/>
    <xsl:param name="graphicsize.extension" select="1"/>
	<xsl:param name="tablecolumns.extension" select="0"/>
    
    <!-- Automatic Labeling -->
    <xsl:param name="section.autolabel" select="1"/>
    <xsl:param name="section.label.includes.component.label" select="1"/>

    <!-- Miscellaneous -->
    <xsl:param name="formal.title.placement">
        figure after
        example before
        equation after
        table before
        procedure before
        task before
    </xsl:param>

    <!-- Graphics -->
    <xsl:param name="make.graphic.viewport" select="0"/>
    <xsl:param name="ignore.image.scaling" select="1"/>

</xsl:stylesheet>