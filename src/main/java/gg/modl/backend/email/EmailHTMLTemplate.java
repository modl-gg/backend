package gg.modl.backend.email;

public interface EmailHTMLTemplate {
    CodeTemplate USER_CODE = (serverName, code) -> new HTMLEmail(
            "%s | Login Code".formatted(serverName),
            """
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; background-color: #f9f9f9; padding: 20px;">
                      <div style="background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                        <h2 style="color: #333; margin-bottom: 20px;">Login Verification Code</h2>
                    
                        <p style="color: #555; font-size: 16px;">
                          Your login verification code for <strong>%s</strong> is:
                        </p>
                    
                        <div style="background-color: #f8f9fa; padding: 20px; border-left: 4px solid #007bff; margin: 20px 0; text-align: center;">
                          <h3 style="margin: 0; color: #333; font-size: 24px; letter-spacing: 3px;">%s</h3>
                        </div>
                    
                        <p style="color: #888; font-size: 14px; margin: 20px 0;">
                          This code will expire in 15 minutes.
                        </p>
                    
                        <div style="border-top: 1px solid #e9ecef; padding-top: 20px; margin-top: 30px;">
                          <p style="color: #6c757d; font-size: 12px; margin: 15px 0 0 0;">
                            This is an automated message. Please do not reply to this email.
                          </p>
                        </div>
                      </div>
                    </div>
                    """.formatted(serverName, code)
    );

    CodeTemplate ADMIN_CODE = (code, __) -> new HTMLEmail(
            "modl.gg | Admin Login Code",
            """
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; background-color: #f9f9f9; padding: 20px;">
                      <div style="background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                        <h2 style="color: #333; margin-bottom: 20px;">Admin Verification Code</h2>
                    
                        <p style="color: #555; font-size: 16px;">
                          Your admin panel login code is:
                        </p>
                    
                        <div style="background-color: #f8f9fa; padding: 20px; border-left: 4px solid #007bff; margin: 20px 0; text-align: center;">
                          <h3 style="margin: 0; color: #333; font-size: 24px; letter-spacing: 3px;">%s</h3>
                        </div>
                    
                        <p style="color: #888; font-size: 14px; margin: 20px 0;">
                          This code will expire in 15 minutes.
                        </p>
                    
                        <div style="border-top: 1px solid #e9ecef; padding-top: 20px; margin-top: 30px;">
                          <p style="color: #6c757d; font-size: 12px; margin: 15px 0 0 0;">
                            This is an automated message. Please do not reply to this email.
                          </p>
                        </div>
                      </div>
                    </div>
                    """.formatted(code));

    VerifyLinkTemplate REGISTRATION_VERIFY_LINK = link -> new HTMLEmail(
            "modl.gg | Verify your email",
            """
                    <p>Please verify your email address by clicking the following link: <a href="%s">%s</a></p>
                    """.formatted(link, link));

    StaffInviteTemplate STAFF_INVITE_TEMPLATE = (serverName, staffRole, link) -> new HTMLEmail(
            "%s | Staff Invitation".formatted(serverName),
            """
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; background-color: #f9f9f9; padding: 20px;">
                      <div style="background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                        <h2 style="color: #333; margin-bottom: 20px;">Team Invitation</h2>
                    
                        <p style="color: #555; font-size: 16px;">
                          You have been invited to join the <strong>%s</strong> team as a <strong>%s</strong>!
                        </p>
                    
                        <div style="background-color: #f8f9fa; padding: 15px; border-left: 4px solid #28a745; margin: 20px 0;">
                          <h4 style="margin: 0 0 10px 0; color: #333;">Welcome to the Team!</h4>
                          <p style="margin: 0; color: #555;">Click the button below to accept your invitation and get started.</p>
                        </div>
                    
                        <div style="text-align: center; margin: 30px 0;">
                          <a href="%s" style="background-color: #28a745; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block; font-weight: bold;">Accept Invitation</a>
                        </div>
                    
                        <p style="color: #888; font-size: 14px; margin: 20px 0;">
                          This invitation will expire in 24 hours.
                        </p>
                    
                        <div style="border-top: 1px solid #e9ecef; padding-top: 20px; margin-top: 30px;">
                          <p style="color: #6c757d; font-size: 12px; margin: 15px 0 0 0;">
                            This is an automated message. Please do not reply to this email.
                          </p>
                        </div>
                      </div>
                    </div>
                    """.formatted(serverName, staffRole, link)
    );

    TicketReplyTemplate TICKET_REPLY_TEMPLATE = (serverName, playerName, isStaffReply, ticketType, ticketId, ticketSubject, replyAuthor, replyContent, ticketUrl) -> new HTMLEmail(
            "%s | Someone replied to Ticket #%s".formatted(serverName, ticketId),
            """
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; background-color: #f9f9f9; padding: 20px;">
                      <div style="background-color: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                        <h2 style="color: #333; margin-bottom: 20px;">Ticket Reply Notification</h2>
                    
                        <p style="color: #555; font-size: 16px;">Hello <strong>%s</strong>,</p>
                    
                        <p style="color: #555; font-size: 16px;">
                          %s has replied to your <strong>%s</strong> ticket:
                        </p>
                    
                        <div style="background-color: #f8f9fa; padding: 15px; border-left: 4px solid #007bff; margin: 20px 0;">
                          <h4 style="margin: 0 0 10px 0; color: #333;">Ticket #%s: %s</h4>
                        </div>
                    
                        <div style="background-color: #fff; border: 1px solid #e9ecef; border-radius: 4px; padding: 15px; margin: 20px 0;">
                          <h5 style="margin: 0 0 10px 0; color: #495057;">Reply from %s:</h5>
                          <p style="margin: 0; color: #333; white-space: pre-wrap;">%s</p>
                        </div>
                    
                        <div style="text-align: center; margin: 30px 0;">
                          <a href="%s" style="background-color: #007bff; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block; font-weight: bold;">View Ticket & Reply</a>
                        </div>
                    
                        <div style="border-top: 1px solid #e9ecef; padding-top: 20px; margin-top: 30px;">
                          <p style="color: #6c757d; font-size: 12px; margin: 15px 0 0 0;">
                            This is an automated message. Please do not reply to this email.
                          </p>
                        </div>
                      </div>
                    </div>
                    """.formatted(playerName, isStaffReply ? "A staff member" : "Someone", ticketType, ticketId, ticketSubject, replyAuthor, replyContent, ticketUrl));

    interface CodeTemplate {
        HTMLEmail build(String serverName, String code);
    }

    interface VerifyLinkTemplate {
        HTMLEmail build(String link);
    }

    interface StaffInviteTemplate {
        HTMLEmail build(String serverName, String staffRole, String link);
    }

    interface TicketReplyTemplate {
        HTMLEmail build(String serverName, String playerName, boolean isStaffReply, String ticketType, String ticketId, String ticketSubject, String replyAuthor, String replyContent, String ticketUrl);
    }

    record HTMLEmail(String subject, String body) {
    }
}
