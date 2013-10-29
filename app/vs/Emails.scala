package org.w3.vs

import org.w3.vs.model.{PasswordResetId, User}

/**
 * An email
 *
 * @param subject the email subject
 * @param recipient the recipient
 * @param from the sender
 * @param text alternative simple text
 * @param html html body
 */
case class EmailMessage(
  subject: String,
  recipient: String,
  from: String,
  text: String,
  html: String)


object Emails {

  /**
   * Registration email
   */
  def registered(user: User)(implicit vs: ValidatorSuite) = new EmailMessage(
    subject = "W3C Validator Suite registration confirmation",
    recipient = user.email,
    from = s"W3C Validator Suite Team <${vs.config.getString("vs.emails.sender").get}>",
    text = views.txt.emails.registered(user).body,
    html = views.html.emails.registered(user).body
  )

  def resetPassword(user: User, id: PasswordResetId)(implicit vs: ValidatorSuite) = new EmailMessage(
    subject = "W3C Validator Suite password reset",
    recipient = user.email,
    from = s"W3C Validator Suite Team <${vs.config.getString("vs.emails.sender").get}>",
    text = views.txt.emails.passwordReset(user, id).body,
    html = views.html.emails.passwordReset(user, id).body
  )

}
