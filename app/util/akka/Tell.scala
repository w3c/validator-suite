package org.w3.util.akkaext

import java.nio.file.Path

case class Tell(path: Path, message: Any)
