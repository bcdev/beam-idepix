<?xml version='1.0'?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format" version="1.0">

    <!--
    <xsl:import href="http://docbook.sourceforge.net/release/xsl/1.73.2/fo/docbook.xsl"/>
    -->
    <xsl:import href="../../docbook-xsl/1.73.2/fo/docbook.xsl"/>

    <!-- Table of contents etc. -->
    <xsl:variable name="generate.toc">
        /appendix toc,title
        article/appendix nop
        /article toc,title
        book toc,title,figure,table,example,equation,index
        /chapter toc,title
        part toc,title
        /preface toc,title
        reference toc,title
        /sect1 toc
        /sect2 toc
        /sect3 toc
        /sect4 toc
        /sect5 toc
        /section toc
        set toc,title
    </xsl:variable>
    <!-- Index generation -->
    <xsl:param name="generate.index" select="1"/>
    <!-- Processor Extensions -->
    <xsl:variable name="fop1.extensions" select="1"/>
    <!-- Stylesheet Extensions -->
    <xsl:variable name="use.extensions" select="1"/>
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
    <!-- Pagination and general styles -->
    <xsl:param name="paper.type">A4</xsl:param>
    <xsl:param name="double.sided" select="1"/>
    <xsl:param name="headers.on.blank.pages" select="0"/>
    <xsl:param name="footers.on.blank.pages" select="0"/>
    <xsl:param name="header.rule" select="0"></xsl:param>
    <xsl:param name="footer.rule" select="0"/>
    <!-- Graphics -->
    <xsl:param name="ignore.image.scaling" select="0"/>
	
	<!-- Center figures and captions -->
	<xsl:attribute-set name="figure.properties">
		<xsl:attribute name="text-align">center</xsl:attribute>
	</xsl:attribute-set>
	
    <!-- Font Families -->
    <!--<xsl:variable name="body.font.family">sans-serif</xsl:variable>-->
    <!-- Page numbering -->
    <xsl:template name="initial.page.number">auto-odd</xsl:template>
    <xsl:template name="page.number.format">1</xsl:template>
    <!-- Running headers -->
    <fo:retrieve-marker
            retrieve-class-name="section.head.marker"
            retrieve-position="first-including-carryover"
            retrieve-boundary="page-sequence"/>

</xsl:stylesheet>
 