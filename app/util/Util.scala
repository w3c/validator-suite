package org.w3.util

import java.io._

object Util {

  /**
   * deletes all the files in a directory (only first level, not recursive)
   */
  def delete(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles foreach delete
    if (!f.delete())
      throw new FileNotFoundException("Failed to delete file: " + f)
  }


}
