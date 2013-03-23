package org.w3.vs.model

/** an unique id to identify the type/class/kind of an Assertion */
case class AssertionTypeId(uniqueId: String) extends AnyVal {
  override def toString = uniqueId
}

object AssertionTypeId {

  /** this should be a field on an Assertion. Let's keep it here until
    * we have a better proposal for the Assertor API
    */
  def apply(assertion: Assertion): AssertionTypeId = {
    apply(assertion.assertor, assertion.title)
  }

  def apply(assertor: AssertorId, title: String): AssertionTypeId = {
    val uniqueId = (assertor, title).hashCode.toString
    new AssertionTypeId(uniqueId)
  }

}

