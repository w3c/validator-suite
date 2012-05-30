package org.w3.util.akkaext

import java.net.URI

case class Tell(path: URI, message: Any)
