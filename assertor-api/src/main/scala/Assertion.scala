package org.w3.assertor

/**
 * an artefact from firing an Assertor
 * 
 * it's important to note that the output targetting the user
 * is done <strong>outside of the Assertion</strong> through
 * the Criterion (found with criterionId)
 * 
 * @param criterionId
 * @param pointer a pointer in the input source
 * @param params used to render the criterion snippet
 */
case class Assertion(
  criterionId: String,
  pointer: Pointer,
  params: Array[String]
)

/** see http://www.w3.org/TR/Pointers-in-RDF10/ */
sealed trait Pointer
sealed trait SinglePointer extends Pointer
sealed trait CoumpoundPointer extends Pointer
case class NoPointer() extends Pointer
case class LineCharPointer(lineNumber: Int, charNumber: Int) extends SinglePointer
case class CharSnippetCompoundPointer(startPointer: SinglePointer, chars: String) extends CoumpoundPointer
