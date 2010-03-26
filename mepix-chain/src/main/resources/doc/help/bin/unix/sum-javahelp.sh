cd ../../xml/sum
TARGET_DIR=../../mepix/

#clean up target directory
rm -f ${TARGET_DIR}/*.png
rm -Rf ${TARGET_DIR}/JavaHelpSearch

#generate JavaHelp files
xsltproc -o ${TARGET_DIR} sum-javahelp.xsl sum.xml

#copy figures
cp figures/*.png ${TARGET_DIR}/figures

#create JavaHelp index
../../javahelp/bin/jhindexer -db ${TARGET_DIR}/JavaHelpSearch ${TARGET_DIR}
