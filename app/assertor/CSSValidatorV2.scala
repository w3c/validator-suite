package org.w3.vs.assertor

import org.w3.vs.web.URL
import org.w3c.css.css._
import org.w3c.css.util.{ Warning => CssWarning, _ }
import java.io._
import org.w3c.www.mime.MimeType
import scalax.io._
import org.w3.vs.model._
import org.w3c.css.parser.CssError
import scala.collection.JavaConverters._
import org.w3c.css.parser.CssParseException

import CSSValidatorV2._

class CSSValidatorV2(
  profile: String = CssVersion.getDefault().toString(),
  medium: String = "all",
  lang: String = "en",
  warninglevel: Int = 2,
  vextwarning: Boolean = false,
  followLinks: Boolean = false
) {

  val ac: ApplContext = {
    val ac = new ApplContext(lang)
    ac.setCssVersionAndProfile(profile)
    ac.setMedium(medium)
    ac.setTreatVendorExtensionsAsWarnings(vextwarning)
    ac.setWarningLevel(warninglevel)
    ac.setFollowlinks(followLinks)
    ac
  }

  def assert(reader: Reader, url: URL, mimeType: MimeType): Iterable[Assertion] = {
    val parser = new DocumentParser(ac, reader, url.toString, mimeType)
    val sheet = parser.getStyleSheet()
    sheet.findConflicts(ac)
    val errors = sheet.getErrors().getErrors().toIterable.map { e =>
      Assertion(e.assertionTypeId, url, id, Vector(e.context), lang, e.title, Error, None)
    }
    val warnings = sheet.getWarnings().getWarnings().toIterable.map { w =>
      Assertion(w.assertionTypeId, url, id, Vector(w.context), lang, w.title, Warning, None)
    }
    errors ++ warnings
  }


}

object CSSValidatorV2 {

  val id = AssertorId("css-val-v2")

  implicit class CssErrorW(val error: CssError) extends AnyVal {
    def title: String = error.getException.getMessage
    def context: Context = {
      val line = error.getLine
      val c = error.getException match {
        case e: CssParseException => e.getContexts.asScala.mkString(" ")
        case _ =>
          println("** unknown error")
          error.getException.printStackTrace()
          ""
      }
      Context(c, Some(line), None)
    }
    def assertionTypeId: AssertionTypeId = AssertionTypeId(CSSValidatorV2.id, title)
  }

  implicit class WarningW(val warning: CssWarning) extends AnyVal {
    def title: String = warning.getWarningMessageEscaped
    def description: String = ""//warning.warningMessage
    def assertionTypeId: AssertionTypeId = AssertionTypeId(CSSValidatorV2.id, title)
    def context: Context = {
      val line = warning.getLine
      val c = try { warning.getContext.getSelectors().asScala.map(_.getName).mkString(" ") } catch { case e: Exception => "" }
      Context(c, Some(line), None)
    }
  }


  def main(args: Array[String]): Unit = {

    val validator = new CSSValidatorV2

    val url = URL("http://www.w3.org/2008/site/css/minimum.css")

    Resource.fromURL(url.toString).acquireAndGet { inputStream =>

      val reader = new InputStreamReader(inputStream)
      val result = validator.assert(reader, url, MimeType.TEXT_CSS)

      result foreach println

    }

  }

}
