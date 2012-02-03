package org.w3

import java.net.{URL => jURL}

package object util {
  
  type Host = String
  type Protocol = String
  type Authority = String
  type Port = Int
  type File = String
  
  type Headers = Map[String, List[String]]
  type ContentType = String
  
}
