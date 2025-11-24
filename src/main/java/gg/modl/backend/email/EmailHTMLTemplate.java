package gg.modl.backend.email;

public interface EmailHTMLTemplate {
    CodeTemplate USER_CODE = """
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
            """::formatted;

    CodeTemplate ADMIN_CODE = (code, __) -> """
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
            """.formatted(code);

    VerifyLinkTemplate REGISTRATION_VERIFY_LINK = link -> """
            <p>Please verify your email address by clicking the following link: <a href="%s">%s</a></p>
            """.formatted(link, link);

    StaffInviteTemplate STAFF_INVITE_TEMPLATE = """
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
            """::formatted;

    interface CodeTemplate {
        String build(String serverName, String code);
    }

    interface VerifyLinkTemplate {
        String build(String link);
    }

    interface StaffInviteTemplate {
        String build(String serverName, String staffRole, String link);
    }
}
