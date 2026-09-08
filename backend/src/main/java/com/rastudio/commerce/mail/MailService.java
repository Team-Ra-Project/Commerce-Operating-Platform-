package com.rastudio.commerce.mail;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends invitation emails through SMTP when SMTP is configured.
 *
 * SMTP is optional during local development. When SMTP is not configured,
 * invitation email sending is skipped instead of preventing the application
 * from starting.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;
    private final boolean smtpConfigured;

    public MailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:no-reply@localhost}") String fromAddress,
            @Value("${app.mail.from-name:RA Studio Commerce Platform}") String fromName,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${spring.mail.username:}") String smtpUsername) {

        /*
         * JavaMailSender is optional because Spring Boot only creates the bean
         * when mail configuration is available. Directly injecting
         * JavaMailSender would make the application fail during startup when
         * SMTP is not configured.
         */
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.fromAddress = fromAddress;
        this.fromName = fromName;

        this.smtpConfigured = this.mailSender != null
                && smtpHost != null
                && !smtpHost.isBlank()
                && smtpUsername != null
                && !smtpUsername.isBlank();
    }

    /**
     * Sends an invitation email.
     *
     * @return true when the email was handed off to the SMTP server;
     *         false when SMTP is not configured or sending failed.
     */
    public boolean sendInvitation(
            String toEmail,
            String inviteeName,
            String organizationName,
            String inviterName,
            String roleLabel,
            String acceptUrl) {

        String subject = "You're invited to join "
                + organizationName
                + " on RA Studio";

        String html = buildInvitationHtml(
                inviteeName,
                organizationName,
                inviterName,
                roleLabel,
                acceptUrl);

        return send(toEmail, subject, html);
    }

    /**
     * Sends a low-stock alert email (Phase 7 — Inventory Management:
     * "triggers a low-stock alert to the Ops Manager (in-app + email)").
     *
     * @return true when the email was handed off to the SMTP server;
     *         false when SMTP is not configured or sending failed.
     */
    public boolean sendLowStockAlert(
            String toEmail,
            String recipientName,
            String organizationName,
            String productName,
            String sku,
            String warehouseName,
            int availableQuantity,
            int threshold) {

        String subject = "Low stock alert: " + productName + " (" + sku + ")";

        String html = buildLowStockHtml(
                recipientName,
                organizationName,
                productName,
                sku,
                warehouseName,
                availableQuantity,
                threshold);

        return send(toEmail, subject, html);
    }

    private String buildLowStockHtml(
            String recipientName,
            String organizationName,
            String productName,
            String sku,
            String warehouseName,
            int availableQuantity,
            int threshold) {

        String safeRecipient = recipientName == null || recipientName.isBlank()
                ? "there"
                : escape(recipientName);

        return "<div style=\"font-family:Arial,Helvetica,sans-serif;"
                + "max-width:560px;margin:0 auto;color:#1F2937;\">"

                + "<h2 style=\"color:#B45309;\">Low stock alert</h2>"

                + "<p>Hi "
                + safeRecipient
                + ",</p>"

                + "<p><strong>"
                + escape(productName)
                + "</strong> (SKU "
                + escape(sku)
                + ") at <strong>"
                + escape(warehouseName)
                + "</strong> has "
                + availableQuantity
                + " unit(s) available, at or below its low-stock threshold of "
                + threshold
                + ".</p>"

                + "<p>Update the stock count in "
                + escape(organizationName)
                + "'s Inventory Management as soon as new stock arrives.</p>"

                + "</div>";
    }

    /**
     * Sends a return/exchange status update email (Phase 9 — Returns & Shipping Management:
     * "customer is notified where the channel supports it" on approve/reject).
     *
     * @return true when the email was handed off to the SMTP server;
     *         false when SMTP is not configured or sending failed.
     */
    public boolean sendReturnStatusUpdate(
            String toEmail,
            String customerName,
            String orderNumber,
            String statusLabel,
            String message) {

        String subject = "Update on your return for order " + orderNumber;
        String html = buildReturnStatusHtml(customerName, orderNumber, statusLabel, message);
        return send(toEmail, subject, html);
    }

    private String buildReturnStatusHtml(String customerName, String orderNumber, String statusLabel, String message) {
        String safeCustomer = customerName == null || customerName.isBlank() ? "there" : escape(customerName);

        return "<div style=\"font-family:Arial,Helvetica,sans-serif;"
                + "max-width:560px;margin:0 auto;color:#1F2937;\">"

                + "<h2 style=\"color:#1D4ED8;\">Return update — order " + escape(orderNumber) + "</h2>"

                + "<p>Hi " + safeCustomer + ",</p>"

                + "<p>Your return request status is now <strong>" + escape(statusLabel) + "</strong>.</p>"

                + (message != null && !message.isBlank() ? "<p>" + escape(message) + "</p>" : "")

                + "</div>";
    }

    /**
     * Sends an already-rendered marketing/campaign email (Phase 19 template content, Phase 20 campaign
     * steps). Reuses the exact same SMTP-configured-or-skip behavior as every other email in this service —
     * added here rather than duplicating the SMTP plumbing in the messaging/retention packages.
     *
     * @return true when the email was handed off to the SMTP server; false when SMTP is not configured or
     *         sending failed (never throws).
     */
    public boolean sendMarketingEmail(String toEmail, String subject, String html) {
        return send(toEmail, subject, html);
    }

    /**
     * Sends a generic automation-rule email. Automation rules provide their
     * own subject and message, so this method only adds a safe, consistent
     * HTML wrapper before reusing the shared SMTP plumbing.
     */
    public boolean sendAutomationEmail(
            String toEmail,
            String recipientName,
            String subject,
            String message) {

        String safeRecipient = recipientName == null || recipientName.isBlank()
                ? "there"
                : escape(recipientName);
        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#1F2937;\">"
                + "<h2 style=\"color:#4338CA;\">" + escape(subject) + "</h2>"
                + "<p>Hi " + safeRecipient + ",</p>"
                + "<p>" + escape(message) + "</p>"
                + "</div>";
        return send(toEmail, subject, html);
    }

    private boolean send(String toEmail, String subject, String html) {
        if (!smtpConfigured) {
            log.warn(
                    "SMTP is not configured. Skipping invitation email to {}. "
                            + "Set SMTP_HOST and SMTP_USERNAME to enable email sending.",
                    toEmail);

            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    "UTF-8");

            helper.setTo(toEmail);
            helper.setFrom(fromAddress, fromName);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);

            log.info("Invitation email sent to {}", toEmail);
            return true;

        } catch (Exception e) {
            log.error(
                    "Failed to send invitation email to {}: {}",
                    toEmail,
                    e.getMessage(),
                    e);

            return false;
        }
    }

    private String buildInvitationHtml(
            String inviteeName,
            String organizationName,
            String inviterName,
            String roleLabel,
            String acceptUrl) {

        String safeInvitee = inviteeName == null || inviteeName.isBlank()
                ? "there"
                : escape(inviteeName);

        String safeOrganizationName = escape(organizationName);
        String safeInviterName = escape(inviterName);
        String safeRoleLabel = escape(roleLabel);
        String safeAcceptUrl = escape(acceptUrl);

        return "<div style=\"font-family:Arial,Helvetica,sans-serif;"
                + "max-width:560px;margin:0 auto;color:#1F2937;\">"

                + "<h2 style=\"color:#111827;\">You're invited to "
                + safeOrganizationName
                + "</h2>"

                + "<p>Hi "
                + safeInvitee
                + ",</p>"

                + "<p>"
                + safeInviterName
                + " has invited you to join <strong>"
                + safeOrganizationName
                + "</strong> on RA Studio's Commerce Operating Platform as "
                + "<strong>"
                + safeRoleLabel
                + "</strong>.</p>"

                + "<p style=\"margin:28px 0;\">"
                + "<a href=\""
                + safeAcceptUrl
                + "\" style=\"background:#4338CA;color:#ffffff;"
                + "padding:12px 22px;border-radius:6px;text-decoration:none;"
                + "font-weight:600;display:inline-block;\">"
                + "Accept Invitation</a>"
                + "</p>"

                + "<p style=\"color:#6B7280;font-size:13px;\">"
                + "Or copy this link into your browser:<br>"
                + "<a href=\""
                + safeAcceptUrl
                + "\">"
                + safeAcceptUrl
                + "</a></p>"

                + "<p style=\"color:#6B7280;font-size:13px;\">"
                + "This invitation link expires in a few days. "
                + "If you weren't expecting this, you can safely ignore this email."
                + "</p>"

                + "</div>";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}