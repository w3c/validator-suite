package org.w3.vs.model

import org.w3.vs._

/** A context for an [[org.w3.vs.validator.Event]]
 *
 *  @param content a code snippet from the source
 *  @param ref a URL reference to the source
 *  @param line an optional line in the source
 *  @param column an optional column in the source
 */
case class Context(content:String, ref:String, line:Option[Int], column:Option[Int])
