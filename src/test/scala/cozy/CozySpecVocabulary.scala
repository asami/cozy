package cozy

import org.goldenport.test.matchers.SpecVocabulary
import org.scalatest.matchers.{MatchResult, Matcher}

/*
 * @since   Jun. 24, 2026
 * @version Jun. 25, 2026
 * @author  ASAMI, Tomoharu
 */
trait CozySpecVocabulary extends SpecVocabulary {
  protected final def include_html(expected: String): Matcher[String] = Matcher { actual =>
    MatchResult(
      actual.contains(expected),
      s"""HTML did not include "$expected"""",
      s"""HTML included "$expected""""
    )
  }

  protected final def include_text(expected: String): Matcher[String] = Matcher { actual =>
    MatchResult(
      actual.contains(expected),
      s"""text did not include "$expected"""",
      s"""text included "$expected""""
    )
  }
}
