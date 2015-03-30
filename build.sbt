import java.io.{StringReader, StringWriter, FileInputStream}
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.{Source, URIResolver, TransformerFactory, Result}
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.{StreamResult, StreamSource}
import net.sf.saxon.TransformerFactoryImpl
import org.xml.sax.{InputSource, EntityResolver}

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

scalacOptions ++= Seq("-unchecked", "-deprecation", "-explaintypes")

//Forcing the scala version to my version to avoid so many warnings
ivyScala := ivyScala.value map {
  _.copy(overrideScalaVersion = true)
}
unmanagedResourceDirectories in Compile <+= baseDirectory { base =>
  base / "src" / "main" / "xmls"
}

resourceGenerators in Compile += xmlTransforms.taskValue

lazy val xmlTransforms = taskKey[Seq[File]]("Do the xml translation insanity")
xmlTransforms := doXmlTranslations(
  baseDirectory.value / "src" / "main" / "xmls",
  baseDirectory.value / "target",
  streams.value.log)

// http://www.scala-sbt.org/0.13/docs/Howto-Generating-Files.html
//Transformation logic: https://github.com/rackerlabs/repose/blob/master/repose-aggregator/commons/configuration/src/main/java/org/openrepose/commons/config/parser/jaxb/UnmarshallerValidator.java#L83
// TODO: this is super nasty. All this stupid xml transformation crap
// TODO: Convert it to scala code in a scala class somewhere the build can use
def doXmlTranslations(base: File, outputBase: File, log: Logger): Seq[File] = {

  val factory = new TransformerFactoryImpl()
  val transformer: TransformerFactory = {
    val instance = TransformerFactory.newInstance()
    val resolver = new URIResolver {
      override def resolve(href: String, xslBase: String): Source = {
        new StreamSource(new FileInputStream(base / "xsl" / "iso-sch" / href))
      }
    }
    instance.setURIResolver(resolver)
    instance
  } //Should be all that's needed


  log.info("Starting transformation insanity")
  val db = {
    val dbf = DocumentBuilderFactory.newInstance()
    dbf.setNamespaceAware(true)
    val stupidResolver = new EntityResolver {
      override def resolveEntity(publicId: String, systemId: String): InputSource = {
        val is = new InputSource()
        is.setSystemId(systemId)
        log.info(s"Looking for ${publicId} or ${systemId}")
        is.setByteStream(new FileInputStream(base / "xsl" / systemId))
        is
      }
    }
    val builder = dbf.newDocumentBuilder()
    builder.setEntityResolver(stupidResolver)
    builder
  }

  def xform(xslSource: StreamSource, sourceDoc: DOMSource, result: Result, params: Map[String, Object] = Map.empty[String, Object]): Unit = {
    val xformer = transformer.newTransformer(xslSource)
    params.foreach { case (k, v) =>
      xformer.setParameter(k, v)
    }
    xformer.transform(sourceDoc, result)
  }

  //All these apply to wadl.sch
  val wadlDoc = db.parse(base / "xsd" / "wadl.sch")
  //dumps it in generated-resources/xml/xslt/wadl.sch
  val firstResult = new StringWriter()
  log.info("Transforming xsd/wadl.sch with xsl/iso-sch/iso_dsdl_include.xsl")
  xform(new StreamSource(new FileInputStream(base / "xsl" / "iso-sch" / "iso_dsdl_include.xsl")),
    new DOMSource(wadlDoc),
    new StreamResult(firstResult))
  val firstResultContent = firstResult.toString
  //Then we take the result of that file, and run these two more transforms on it, producing a new .sch and a .xsl
  //Those go into the generated resources
  //Need to have somewhere to put the generated sources
  (outputBase / "xml" / "xslt").mkdirs()
  val secondOutput = outputBase / "xml" / "xslt" / "wadl.sch"
  val thirdOutput = outputBase / "xml" / "xslt" / "wadl.xsl"
  log.info("Transforming generated wadl.sch with xsl/iso-sch/iso_abstract_expand.xsl")

  xform(
    new StreamSource(new FileInputStream(base / "xsl" / "iso-sch" / "iso_abstract_expand.xsl")),
    new DOMSource(db.parse(new InputSource(new StringReader(firstResultContent)))),
    new StreamResult(secondOutput))

  log.info("Transforming generated wadl.sch with xsl/iso-sch/iso_svrl_custom_xslt2.xsl into wadl.xsl")
  val third = xform(
    new StreamSource(new FileInputStream(base / "xsl" / "iso-sch" / "iso_svrl_custom_xslt2.xsl")),
    new DOMSource(db.parse(new InputSource(new StringReader(firstResultContent)))),
    new StreamResult(thirdOutput),
    Map("select-contexts" -> "key")
  )
  log.info("Completed")
  Seq(secondOutput, thirdOutput)
}