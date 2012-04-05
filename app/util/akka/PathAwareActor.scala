package org.w3.util.akkaext

import akka.actor.Actor
import java.nio.file.{ Path, Paths }

trait PathAwareActor { self: Actor =>

  def selfPath: Path = Paths.get(self.self.path.toString)

  object Child {
    def unapply(path: Path): Option[String] = {
      val name = selfPath.relativize(path).subpath(0, 1).toString
      if (name.isEmpty) None else Some(name)
    }
  }



}
