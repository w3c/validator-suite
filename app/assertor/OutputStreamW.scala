package org.w3.vs.assertor

import java.io._

object OutputStreamW {
  implicit def pimp(out: OutputStream) = new OutputStreamW(out)
}

class OutputStreamW(val out: OutputStream) extends AnyVal {
  def write(s: String)(implicit charset: String = "UTF-8"): Unit =
    out.write(s.getBytes(charset))
  def writeCRLN(s: String)(implicit charset: String = "UTF-8"): Unit =
    write(s + "\r\n")
}
