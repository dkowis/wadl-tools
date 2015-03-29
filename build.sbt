name := "wadl-tools"
version := "1.0.30"
scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % "2.10.3",
  "net.sf.saxon" % "Saxon-HE" % "9.4.0-9",
  "com.rackspace.apache" % "xerces2-xsd11" % "2.11.2",
  "xalan" % "xalan" % "2.7.1",
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
  "junit" % "junit" % "4.10" % "test",
  "org.scalatest" %% "scalatest" % "2.0" % "test",
  "org.apache.santuario" % "xmlsec" % "1.4.6" % "test",
  "org.slf4j" % "slf4j-log4j12" % "1.7.7"
)

//Forcing the scala version to my version to avoid so many warnings
ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

unmanagedResourceDirectories in Compile ++= Seq(
  baseDirectory.value / "wadl",
  baseDirectory.value / "xsd",
  baseDirectory.value / "xsl"
)
//TODO: include generated-resources/xml/xslt/wadl.xsl

//TODO: write an XSLT transforming function, and then make some tasks?
// targetDirectory, fileList, stylesheet, possibly with some parameters
// Use Saxon-HE to do it, so that needs to be part of this guy's classpath

def doXmlTranslations(base: File): Seq[File] = {
  ???
}