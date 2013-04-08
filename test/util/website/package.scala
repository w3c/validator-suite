package org.w3.vs.util

package object website {
  
  /** pimps [[java.lang.String]] to [[org.w3.vs.website.StringW]]
   *  so that methods `-->` and `--->` are made available
   */
  implicit val wrapString = org.w3.vs.util.website.StringW.wrapString _
  
}