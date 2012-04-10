package org.w3.util.akkaext

import akka.actor.Actor
import java.nio.file.{ Path, Paths }
import java.net.URI

trait PathAwareActor { this: Actor =>

  def selfPath: Path = Paths.get(new URI(self.path.toString).getPath)

  object Child {
    def unapply(path: Path): Option[String] = {
      val name = selfPath.relativize(path).subpath(0, 1).toString
      if (name.isEmpty) None else Some(name)
    }
  }

}
