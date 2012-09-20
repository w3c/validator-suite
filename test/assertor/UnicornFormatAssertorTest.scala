package org.w3.vs.assertor

import org.w3.vs.model._
import org.w3.util.URL
import org.specs2.mutable._
import akka.util.duration._
import org.w3.vs.view.Helper
import io.Source

object UnicornFormatAssertorTest extends Specification with AssertionResultMatcher {

  val source = new Source {
    protected val iter: Iterator[Char] = { //[&lt; &gt; &amp; &#39; &amp;#39;]
      """
        |<observationresponse xmlns="http://www.w3.org/2009/10/unicorn/observationresponse" ref="http://test.vs?p=1&amp;p=2" date="2012-09-20T01:48:31Z" xml:lang="en">
        |  <message type="error">
        |    <title>
        |      Property doesn't exist. ## &amp;#39; &lt; &gt; &#39; &amp; ## <a href="?p=1&amp;p=2">test</a>
        |    </title>
        |    <description>
        |      <code>&lt;p&gt; test &lt;/p&gt;</code>
        |    </description>
        |  </message>
        |</observationresponse>
      """.stripMargin.iterator
    }
  }

  val assertor = new UnicornFormatAssertor {
    def id: AssertorId = AssertorId("test_assertor")
  }

  val assertion = assertor.assert(source).head

  "A UnicornFormat Assertor must unescape urls correctly" in {

    assertion.url must beEqualTo (URL("http://test.vs?p=1&p=2"))

  }

  "A UnicornFormat Assertor must unescape the title correctly" in {

    assertion.title must beEqualTo ("""Property doesn't exist. ## ' &lt; &gt; ' &amp; ## <a href="?p=1&amp;p=2">test</a>""") // [&lt; &gt; & ' ']

  }
  "A UnicornFormat Assertor must unescape the description correctly" in {

    assertion.description must beEqualTo (Some("<code>&lt;p&gt; test &lt;/p&gt;</code>"))

  }

}
