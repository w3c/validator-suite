package org.w3.vs.util

import java.io._
import scala.concurrent.{ Future, ExecutionContext }
import com.codahale.metrics._
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.util.{ Success, Failure, Try }
import org.w3.vs.web._

object file {

  /**
   * deletes all the files in a directory (only first level, not recursive)
   * note: there is still an issue with Windows
   */
  def delete(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles foreach delete
    if (!f.delete())
      throw new FileNotFoundException("Failed to delete file: " + f)
  }

}
