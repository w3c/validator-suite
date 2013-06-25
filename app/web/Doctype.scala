package org.w3.vs.web

case class Doctype(name: String, publicId: String, systemId: String) {

  def isHtml5: Boolean =
    name == "html" && publicId.isEmpty && systemId.isEmpty

}
