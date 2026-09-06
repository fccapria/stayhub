package com.stayhub.backend.service;

import com.stayhub.backend.entity.Booking;
import com.stayhub.backend.entity.Payment;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final PdfReceiptService pdfReceiptService;

    @Value("${stayhub.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${stayhub.mail.from:StayHub Reservations <noreply@stayhub.com>}")
    private String fromAddress;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("€ #,##0.00", new DecimalFormatSymbols(Locale.ITALY));

    @Async
    public void sendBookingConfirmation(Booking booking, Payment payment) {
        if (!mailEnabled) {
            logger.info("Notifiche email disabilitate (stayhub.mail.enabled=false). Invio ignorato per prenotazione #{}", booking.getId());
            return;
        }

        if (booking.getUser() == null || booking.getUser().getEmail() == null || booking.getUser().getEmail().trim().isEmpty()) {
            logger.warn("Impossibile inviare email per prenotazione #{}: indirizzo email utente non presente.", booking.getId());
            return;
        }

        String recipientEmail = booking.getUser().getEmail().trim();

        // Ignora domini fittizi di test generati dagli script di collaudo automatico e demo
        if (recipientEmail.toLowerCase().endsWith("@test.stayhub.com")
                || recipientEmail.toLowerCase().endsWith("@stayhub.com")
                || recipientEmail.toLowerCase().endsWith("@example.com")
                || recipientEmail.toLowerCase().endsWith(".test")) {
            logger.info("ℹ️ [EMAIL TEST/DEMO] Dominio fittizio rilevato ('{}'). Invio SMTP reale saltato per evitare bounce di mancata consegna.", recipientEmail);
            return;
        }

        if (mailPassword == null || mailPassword.trim().isEmpty()) {
            logger.info("ℹ️ [EMAIL SIMULATA] Password SMTP non configurata in application.properties. " +
                    "Prenotazione #{} pronta per l'invio a '{}'. Configura SPRING_MAIL_PASSWORD per la consegna reale.",
                    booking.getId(), recipientEmail);
            return;
        }

        if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl jms) {
            jms.setPassword(mailPassword.replace(" ", "").trim());
        }

        try {
            logger.info("Preparazione email di conferma con ricevuta PDF per prenotazione #{} a {}", booking.getId(), recipientEmail);

            // 1. Generazione della Ricevuta PDF in memoria
            ByteArrayOutputStream pdfStream = new ByteArrayOutputStream();
            pdfReceiptService.generateReceiptPdf(booking, payment, pdfStream);
            byte[] pdfBytes = pdfStream.toByteArray();

            // 2. Costruzione MimeMessage
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(recipientEmail);
            helper.setSubject("Conferma prenotazione #" + booking.getId() + " - " + booking.getRoom().getName());

            // 3. Template HTML brandizzato StayHub
            String htmlContent = buildEmailTemplate(booking, payment);
            helper.setText(htmlContent, true);

            // 4. Allegato PDF
            String attachmentName = "ricevuta-prenotazione-" + booking.getId() + ".pdf";
            helper.addAttachment(attachmentName, new ByteArrayResource(pdfBytes), "application/pdf");

            // 5. Invio effettivo
            mailSender.send(mimeMessage);
            logger.info("✔ Email di conferma e ricevuta PDF inviate con successo a {}", recipientEmail);

        } catch (Exception e) {
            logger.error("✘ Errore durante l'invio dell'email per la prenotazione #{}: {}", booking.getId(), e.getMessage(), e);
            // Non rilanciamo l'eccezione per non compromettere la transazione di prenotazione
        }
    }

    private String buildEmailTemplate(Booking booking, Payment payment) {
        String guestName = "Gentile Ospite";
        if (booking.getUser() != null) {
            String first = booking.getUser().getFirstName() != null ? booking.getUser().getFirstName().trim() : "";
            String last = booking.getUser().getLastName() != null ? booking.getUser().getLastName().trim() : "";
            String full = (first + " " + last).trim();
            if (!full.isEmpty()) {
                guestName = full;
            }
        }

        String roomName = booking.getRoom() != null ? booking.getRoom().getName() : "Alloggio StayHub";
        int capacity = booking.getRoom() != null ? booking.getRoom().getCapacity() : 2;
        String checkInStr = booking.getCheckIn() != null ? booking.getCheckIn().format(DATE_FORMATTER) : "-";
        String checkOutStr = booking.getCheckOut() != null ? booking.getCheckOut().format(DATE_FORMATTER) : "-";
        long nights = (booking.getCheckIn() != null && booking.getCheckOut() != null)
                ? ChronoUnit.DAYS.between(booking.getCheckIn(), booking.getCheckOut()) : 1;
        String nightsStr = nights + " " + (nights == 1 ? "notte" : "notti");
        String totalPriceStr = booking.getTotalPrice() != null ? CURRENCY_FORMAT.format(booking.getTotalPrice()) : "€ 0.00";

        String paymentMethodStr = payment != null && payment.getPaymentMethod() != null
                ? payment.getPaymentMethod().name().replace("_", " ") : "CARTA DI CREDITO";
        String transactionRef = payment != null && payment.getTransactionReference() != null
                ? payment.getTransactionReference() : "TX-STAYHUB-" + booking.getId();

        return "<!DOCTYPE html>" +
                "<html lang='it'>" +
                "<head>" +
                "  <meta charset='UTF-8'>" +
                "  <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "  <title>Conferma Prenotazione StayHub</title>" +
                "  <style>" +
                "    body { margin: 0; padding: 0; background-color: #f4f4f5; font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #09090b; -webkit-font-smoothing: antialiased; }" +
                "    table { border-collapse: collapse; }" +
                "    .wrapper { width: 100%; background-color: #f4f4f5; padding: 32px 12px; }" +
                "    .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; border: 1px solid #e4e4e7; box-shadow: 0 4px 20px rgba(0,0,0,0.03); }" +
                "    .top-accent-bar { height: 4px; background: linear-gradient(90deg, #0284c7, #38bdf8); }" +
                "    .header { padding: 30px 32px 24px; text-align: center; background: #ffffff; border-bottom: 1px solid #e4e4e7; }" +
                "    .logo-text { font-size: 28px; font-weight: 800; color: #09090b; letter-spacing: -0.5px; text-decoration: none; }" +
                "    .logo-accent { color: #0284c7; }" +
                "    .logo-icon { display: inline-block; vertical-align: middle; margin-right: 6px; font-size: 22px; }" +
                "    .subtitle { font-size: 11px; color: #9ca3af; text-transform: uppercase; letter-spacing: 1.5px; margin-top: 6px; font-weight: 600; }" +
                "    .content { padding: 32px; }" +
                "    .greeting { font-size: 22px; font-weight: 700; color: #09090b; margin-bottom: 8px; letter-spacing: -0.02em; }" +
                "    .intro { font-size: 15px; line-height: 1.6; color: #4b5563; margin-bottom: 26px; }" +
                "    .card { background: #ffffff; border: 1px solid #e4e4e7; border-radius: 10px; padding: 24px; margin-bottom: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.02); }" +
                "    .card-header { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 16px; border-bottom: 1px solid #f4f4f5; padding-bottom: 12px; }" +
                "    .room-title { font-size: 18px; font-weight: 700; color: #09090b; }" +
                "    .booking-ref { font-family: monospace; font-size: 12px; color: #9ca3af; background: #f4f4f5; padding: 2px 8px; border-radius: 4px; }" +
                "    .dates-container { background: #f4f4f5; border: 1px solid #e4e4e7; border-radius: 8px; padding: 14px 18px; margin-bottom: 20px; }" +
                "    .date-col { display: inline-block; vertical-align: middle; }" +
                "    .date-label { font-size: 10px; text-transform: uppercase; letter-spacing: 0.08em; color: #9ca3af; font-weight: 600; margin-bottom: 2px; }" +
                "    .date-value { font-size: 14px; font-weight: 700; color: #09090b; }" +
                "    .date-arrow { display: inline-block; vertical-align: middle; color: #0284c7; font-size: 18px; padding: 0 16px; }" +
                "    .nights-pill { display: inline-block; vertical-align: middle; background: #e0f2fe; color: #0369a1; border-radius: 9999px; padding: 3px 10px; font-size: 11px; font-weight: 700; margin-left: 10px; }" +
                "    .meta-table { width: 100%; font-size: 14px; margin-bottom: 12px; }" +
                "    .meta-table td { padding: 9px 0; border-bottom: 1px solid #f4f4f5; }" +
                "    .meta-label { color: #6b7280; font-weight: 500; width: 45%; }" +
                "    .meta-value { color: #09090b; font-weight: 600; text-align: right; width: 55%; }" +
                "    .code-badge { font-family: monospace; font-size: 12px; background: #f4f4f5; border: 1px solid #e4e4e7; border-radius: 4px; padding: 3px 8px; color: #4b5563; }" +
                "    .status-badge { display: inline-block; padding: 3px 10px; background: rgba(16, 185, 129, 0.12); color: #059669; border: 1px solid rgba(16, 185, 129, 0.3); border-radius: 6px; font-weight: 700; font-size: 11px; letter-spacing: 0.04em; text-transform: uppercase; }" +
                "    .price-row { padding-top: 14px; border-top: 2px dashed #e4e4e7; margin-top: 10px; }" +
                "    .price-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; color: #9ca3af; font-weight: 700; }" +
                "    .price-value { font-size: 24px; font-weight: 800; color: #0284c7; text-align: right; }" +
                "    .notice-box { background: #f0f9ff; border: 1px solid #bae6fd; border-left: 4px solid #0284c7; padding: 14px 18px; border-radius: 8px; font-size: 13px; color: #0369a1; line-height: 1.5; margin-bottom: 28px; }" +
                "    .btn-container { text-align: center; margin: 26px 0 10px; }" +
                "    .btn-primary { display: inline-block; background-color: #0284c7; color: #ffffff !important; text-decoration: none; padding: 13px 30px; border-radius: 8px; font-weight: 600; font-size: 14px; box-shadow: 0 4px 14px rgba(2, 132, 199, 0.25); text-align: center; }" +
                "    .footer { background: #f4f4f5; padding: 24px; text-align: center; font-size: 12px; color: #9ca3af; border-top: 1px solid #e4e4e7; line-height: 1.6; }" +
                "  </style>" +
                "</head>" +
                "<body>" +
                "  <div class='wrapper'>" +
                "    <table class='container' role='presentation' cellpadding='0' cellspacing='0' width='100%' align='center' style='max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e4e4e7;'>" +
                "      <tr>" +
                "        <td class='top-accent-bar' style='height: 4px; background: #0284c7;'></td>" +
                "      </tr>" +
                "      <tr>" +
                "        <td class='header' style='padding: 24px 32px 20px; text-align: center; background: #ffffff; border-bottom: 1px solid #e4e4e7;'>" +
                "          <div class='logo-text' style='font-size: 28px; font-weight: 800; color: #09090b; letter-spacing: -0.5px;'>" +
                "            <span style='color: #0284c7; font-size: 26px;'>🏠</span> Stay<span style='color: #0284c7;'>Hub</span>" +
                "          </div>" +
                "        </td>" +
                "      </tr>" +
                "      <tr>" +
                "        <td class='content' style='padding: 32px;'>" +
                "          <div class='greeting' style='font-size: 22px; font-weight: 700; color: #09090b; margin-bottom: 8px;'>Ciao " + guestName + ",</div>" +
                "          <div class='intro' style='font-size: 15px; line-height: 1.6; color: #4b5563; margin-bottom: 24px;'>" +
                "            La tua prenotazione per <strong>" + roomName + "</strong> è confermata. Di seguito trovi il riepilogo del tuo soggiorno." +
                "          </div>" +
                "          <table class='card' cellpadding='0' cellspacing='0' width='100%' style='background: #ffffff; border: 1px solid #e4e4e7; border-radius: 10px; padding: 22px; margin-bottom: 24px;'>" +
                "            <tr>" +
                "              <td style='padding-bottom: 14px; border-bottom: 1px solid #f4f4f5;'>" +
                "                <table width='100%'><tr>" +
                "                  <td style='font-size: 18px; font-weight: 700; color: #09090b;'>" + roomName + "</td>" +
                "                  <td style='text-align: right;'><span style='font-family: monospace; font-size: 12px; color: #9ca3af; background: #f4f4f5; padding: 3px 8px; border-radius: 4px;'>ID: #" + booking.getId() + "</span></td>" +
                "                </tr></table>" +
                "              </td>" +
                "            </tr>" +
                "            <tr>" +
                "              <td style='padding: 18px 0 14px;'>" +
                "                <table width='100%' style='background: #f4f4f5; border: 1px solid #e4e4e7; border-radius: 8px; padding: 12px 16px;'>" +
                "                  <tr>" +
                "                    <td style='width: 40%;'>" +
                "                      <div style='font-size: 10px; text-transform: uppercase; letter-spacing: 0.08em; color: #9ca3af; font-weight: 600;'>CHECK-IN</div>" +
                "                      <div style='font-size: 14px; font-weight: 700; color: #09090b; margin-top: 2px;'>" + checkInStr + "</div>" +
                "                    </td>" +
                "                    <td style='width: 20%; text-align: center; color: #0284c7; font-size: 18px;'>➔</td>" +
                "                    <td style='width: 40%; text-align: right;'>" +
                "                      <div style='font-size: 10px; text-transform: uppercase; letter-spacing: 0.08em; color: #9ca3af; font-weight: 600;'>CHECK-OUT</div>" +
                "                      <div style='font-size: 14px; font-weight: 700; color: #09090b; margin-top: 2px;'>" + checkOutStr + "</div>" +
                "                    </td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td colspan='3' style='text-align: center; padding-top: 8px;'>" +
                "                      <span style='background: #e0f2fe; color: #0369a1; border-radius: 9999px; padding: 3px 12px; font-size: 11px; font-weight: 700;'>" + nightsStr + "</span>" +
                "                    </td>" +
                "                  </tr>" +
                "                </table>" +
                "              </td>" +
                "            </tr>" +
                "            <tr>" +
                "              <td style='padding-top: 6px;'>" +
                "                <table class='meta-table' width='100%' cellpadding='0' cellspacing='0'>" +
                "                  <tr>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #6b7280; font-size: 14px;'>Ospiti ammessi:</td>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #09090b; font-weight: 600; text-align: right; font-size: 14px;'>" + capacity + " persone</td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #6b7280; font-size: 14px;'>Metodo di Pagamento:</td>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #09090b; font-weight: 600; text-align: right; font-size: 14px;'>" + paymentMethodStr + "</td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #6b7280; font-size: 14px;'>Riferimento Transazione:</td>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; text-align: right;'><span style='font-family: monospace; font-size: 12px; background: #f4f4f5; border: 1px solid #e4e4e7; border-radius: 4px; padding: 2px 7px; color: #4b5563;'>" + transactionRef + "</span></td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #6b7280; font-size: 14px;'>Stato Prenotazione:</td>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; text-align: right;'><span style='display: inline-block; padding: 3px 10px; background: rgba(16, 185, 129, 0.12); color: #059669; border: 1px solid rgba(16, 185, 129, 0.3); border-radius: 6px; font-weight: 700; font-size: 11px; text-transform: uppercase;'>CONFERMATA</span></td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td style='padding-top: 14px; color: #9ca3af; font-size: 11px; text-transform: uppercase; font-weight: 700;'>Totale Soggiorno (IVA incl.)</td>" +
                "                    <td style='padding-top: 14px; text-align: right; color: #0284c7; font-size: 24px; font-weight: 800;'>" + totalPriceStr + "</td>" +
                "                  </tr>" +
                "                </table>" +
                "              </td>" +
                "            </tr>" +
                "          </table>" +
                "          <div class='notice-box' style='background: #f0f9ff; border: 1px solid #bae6fd; border-left: 4px solid #0284c7; padding: 14px 18px; border-radius: 8px; font-size: 13px; color: #0369a1; line-height: 1.5; margin-bottom: 26px;'>" +
                "            📎 In allegato trovi la ricevuta della prenotazione in formato PDF." +
                "          </div>" +
                "          <div class='btn-container' style='text-align: center; margin: 26px 0 10px;'>" +
                "            <a href='http://localhost:4200/bookings/my' class='btn-primary' style='display: inline-block; background-color: #0284c7; color: #ffffff !important; text-decoration: none; padding: 13px 30px; border-radius: 8px; font-weight: 600; font-size: 14px;'>I miei viaggi</a>" +
                "          </div>" +
                "        </td>" +
                "      </tr>" +
                "      <tr>" +
                "        <td class='footer' style='background: #f4f4f5; padding: 24px; text-align: center; font-size: 12px; color: #9ca3af; border-top: 1px solid #e4e4e7; line-height: 1.6;'>" +
                "          © 2026 StayHub • Per informazioni o richieste rispondi a questa email o scrivi a support@stayhub.com." +
                "        </td>" +
                "      </tr>" +
                "    </table>" +
                "  </div>" +
                "</body>" +
                "</html>";
    }

    @Async
    public void sendHostBookingNotification(Booking booking, Payment payment) {
        if (!mailEnabled) {
            logger.info("Notifiche email disabilitate (stayhub.mail.enabled=false). Invio notifica host ignorato per prenotazione #{}", booking.getId());
            return;
        }

        if (booking.getRoom() == null || booking.getRoom().getOwner() == null ||
                booking.getRoom().getOwner().getEmail() == null ||
                booking.getRoom().getOwner().getEmail().trim().isEmpty()) {
            logger.warn("Impossibile inviare notifica email all'host per prenotazione #{}: email del locatore non presente.", booking.getId());
            return;
        }

        String hostEmail = booking.getRoom().getOwner().getEmail().trim();

        // Ignora domini fittizi di test generati dagli script di collaudo automatico e demo
        if (hostEmail.toLowerCase().endsWith("@test.stayhub.com")
                || hostEmail.toLowerCase().endsWith("@stayhub.com")
                || hostEmail.toLowerCase().endsWith("@example.com")
                || hostEmail.toLowerCase().endsWith(".test")) {
            logger.info("ℹ️ [EMAIL TEST/DEMO] Dominio host fittizio rilevato ('{}'). Invio SMTP reale saltato per evitare bounce di mancata consegna.", hostEmail);
            return;
        }

        if (mailPassword == null || mailPassword.trim().isEmpty()) {
            logger.info("ℹ️ [EMAIL SIMULATA] Password SMTP non configurata in application.properties. " +
                    "Notifica nuova prenotazione #{} pronta per l'invio all'host '{}'. Configura SPRING_MAIL_PASSWORD per la consegna reale.",
                    booking.getId(), hostEmail);
            return;
        }

        if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl jms) {
            jms.setPassword(mailPassword.replace(" ", "").trim());
        }

        try {
            logger.info("Preparazione email notifica nuova prenotazione #{} per l'host {}", booking.getId(), hostEmail);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(hostEmail);
            String roomName = booking.getRoom() != null ? booking.getRoom().getName() : "Alloggio";
            helper.setSubject("Nuova prenotazione ricevuta! #" + booking.getId() + " - " + roomName);

            String htmlContent = buildHostEmailTemplate(booking, payment);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            logger.info("✔ Notifica email nuova prenotazione inviata con successo all'host {}", hostEmail);

        } catch (Exception e) {
            logger.error("✘ Errore durante l'invio dell'email all'host per la prenotazione #{}: {}", booking.getId(), e.getMessage(), e);
        }
    }

    private String buildHostEmailTemplate(Booking booking, Payment payment) {
        String hostName = "Gentile Locatore";
        if (booking.getRoom() != null && booking.getRoom().getOwner() != null) {
            String first = booking.getRoom().getOwner().getFirstName() != null ? booking.getRoom().getOwner().getFirstName().trim() : "";
            String last = booking.getRoom().getOwner().getLastName() != null ? booking.getRoom().getOwner().getLastName().trim() : "";
            String full = (first + " " + last).trim();
            if (!full.isEmpty()) {
                hostName = full;
            }
        }

        String guestName = "Ospite StayHub";
        String guestEmail = "-";
        if (booking.getUser() != null) {
            String first = booking.getUser().getFirstName() != null ? booking.getUser().getFirstName().trim() : "";
            String last = booking.getUser().getLastName() != null ? booking.getUser().getLastName().trim() : "";
            String full = (first + " " + last).trim();
            if (!full.isEmpty()) {
                guestName = full;
            }
            if (booking.getUser().getEmail() != null) {
                guestEmail = booking.getUser().getEmail().trim();
            }
        }

        String roomName = booking.getRoom() != null ? booking.getRoom().getName() : "Alloggio StayHub";
        int capacity = booking.getRoom() != null ? booking.getRoom().getCapacity() : 2;
        String checkInStr = booking.getCheckIn() != null ? booking.getCheckIn().format(DATE_FORMATTER) : "-";
        String checkOutStr = booking.getCheckOut() != null ? booking.getCheckOut().format(DATE_FORMATTER) : "-";
        long nights = (booking.getCheckIn() != null && booking.getCheckOut() != null)
                ? ChronoUnit.DAYS.between(booking.getCheckIn(), booking.getCheckOut()) : 1;
        String nightsStr = nights + " " + (nights == 1 ? "notte" : "notti");
        String totalPriceStr = booking.getTotalPrice() != null ? CURRENCY_FORMAT.format(booking.getTotalPrice()) : "€ 0.00";

        String paymentMethodStr = payment != null && payment.getPaymentMethod() != null
                ? payment.getPaymentMethod().name().replace("_", " ") : "CARTA DI CREDITO";
        String transactionRef = payment != null && payment.getTransactionReference() != null
                ? payment.getTransactionReference() : "TX-STAYHUB-" + booking.getId();

        return "<!DOCTYPE html>" +
                "<html lang='it'>" +
                "<head>" +
                "  <meta charset='UTF-8'>" +
                "  <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "  <title>Nuova Prenotazione Ricevuta - StayHub</title>" +
                "  <style>" +
                "    body { margin: 0; padding: 0; background-color: #f4f4f5; font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #09090b; -webkit-font-smoothing: antialiased; }" +
                "    table { border-collapse: collapse; }" +
                "    .wrapper { width: 100%; background-color: #f4f4f5; padding: 32px 12px; }" +
                "    .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; border: 1px solid #e4e4e7; box-shadow: 0 4px 20px rgba(0,0,0,0.03); }" +
                "    .top-accent-bar { height: 4px; background: linear-gradient(90deg, #0284c7, #38bdf8); }" +
                "    .header { padding: 24px 32px 20px; text-align: center; background: #ffffff; border-bottom: 1px solid #e4e4e7; }" +
                "    .logo-text { font-size: 28px; font-weight: 800; color: #09090b; letter-spacing: -0.5px; }" +
                "    .content { padding: 32px; }" +
                "    .greeting { font-size: 22px; font-weight: 700; color: #09090b; margin-bottom: 8px; }" +
                "    .intro { font-size: 15px; line-height: 1.6; color: #4b5563; margin-bottom: 24px; }" +
                "    .card { background: #ffffff; border: 1px solid #e4e4e7; border-radius: 10px; padding: 22px; margin-bottom: 24px; }" +
                "    .meta-table { width: 100%; font-size: 14px; margin-bottom: 12px; }" +
                "    .meta-table td { padding: 8px 0; border-bottom: 1px solid #f4f4f5; }" +
                "    .meta-label { color: #6b7280; font-weight: 500; width: 45%; }" +
                "    .meta-value { color: #09090b; font-weight: 600; text-align: right; width: 55%; }" +
                "    .notice-box { background: #f0fdf4; border: 1px solid #bbf7d0; border-left: 4px solid #16a34a; padding: 14px 18px; border-radius: 8px; font-size: 13px; color: #15803d; line-height: 1.5; margin-bottom: 26px; }" +
                "    .btn-container { text-align: center; margin: 26px 0 10px; }" +
                "    .btn-primary { display: inline-block; background-color: #0284c7; color: #ffffff !important; text-decoration: none; padding: 13px 30px; border-radius: 8px; font-weight: 600; font-size: 14px; box-shadow: 0 4px 14px rgba(2, 132, 199, 0.25); text-align: center; }" +
                "    .footer { background: #f4f4f5; padding: 24px; text-align: center; font-size: 12px; color: #9ca3af; border-top: 1px solid #e4e4e7; line-height: 1.6; }" +
                "  </style>" +
                "</head>" +
                "<body>" +
                "  <div class='wrapper'>" +
                "    <table class='container' role='presentation' cellpadding='0' cellspacing='0' width='100%' align='center' style='max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e4e4e7;'>" +
                "      <tr>" +
                "        <td class='top-accent-bar' style='height: 4px; background: #0284c7;'></td>" +
                "      </tr>" +
                "      <tr>" +
                "        <td class='header' style='padding: 24px 32px 20px; text-align: center; background: #ffffff; border-bottom: 1px solid #e4e4e7;'>" +
                "          <div class='logo-text' style='font-size: 28px; font-weight: 800; color: #09090b; letter-spacing: -0.5px;'>" +
                "            <span style='color: #0284c7; font-size: 26px;'>🏠</span> Stay<span style='color: #0284c7;'>Hub</span>" +
                "          </div>" +
                "          <div style='font-size: 11px; color: #9ca3af; text-transform: uppercase; letter-spacing: 1.5px; margin-top: 6px; font-weight: 600;'>PANNELLO LOCATORI</div>" +
                "        </td>" +
                "      </tr>" +
                "      <tr>" +
                "        <td class='content' style='padding: 32px;'>" +
                "          <div class='greeting' style='font-size: 22px; font-weight: 700; color: #09090b; margin-bottom: 8px;'>Ciao " + hostName + ",</div>" +
                "          <div class='intro' style='font-size: 15px; line-height: 1.6; color: #4b5563; margin-bottom: 24px;'>" +
                "            Ottime notizie! È stata appena confermata una nuova prenotazione per il tuo alloggio <strong>" + roomName + "</strong>." +
                "          </div>" +
                "          <table class='card' cellpadding='0' cellspacing='0' width='100%' style='background: #ffffff; border: 1px solid #e4e4e7; border-radius: 10px; padding: 22px; margin-bottom: 24px;'>" +
                "            <tr>" +
                "              <td style='padding-bottom: 14px; border-bottom: 1px solid #f4f4f5;'>" +
                "                <table width='100%'><tr>" +
                "                  <td style='font-size: 18px; font-weight: 700; color: #09090b;'>" + roomName + "</td>" +
                "                  <td style='text-align: right;'><span style='font-family: monospace; font-size: 12px; color: #9ca3af; background: #f4f4f5; padding: 3px 8px; border-radius: 4px;'>Prenotazione: #" + booking.getId() + "</span></td>" +
                "                </tr></table>" +
                "              </td>" +
                "            </tr>" +
                "            <tr>" +
                "              <td style='padding: 18px 0 14px;'>" +
                "                <table width='100%' style='background: #f4f4f5; border: 1px solid #e4e4e7; border-radius: 8px; padding: 12px 16px;'>" +
                "                  <tr>" +
                "                    <td style='width: 40%;'>" +
                "                      <div style='font-size: 10px; text-transform: uppercase; letter-spacing: 0.08em; color: #9ca3af; font-weight: 600;'>CHECK-IN</div>" +
                "                      <div style='font-size: 14px; font-weight: 700; color: #09090b; margin-top: 2px;'>" + checkInStr + "</div>" +
                "                    </td>" +
                "                    <td style='width: 20%; text-align: center; color: #0284c7; font-size: 18px;'>➔</td>" +
                "                    <td style='width: 40%; text-align: right;'>" +
                "                      <div style='font-size: 10px; text-transform: uppercase; letter-spacing: 0.08em; color: #9ca3af; font-weight: 600;'>CHECK-OUT</div>" +
                "                      <div style='font-size: 14px; font-weight: 700; color: #09090b; margin-top: 2px;'>" + checkOutStr + "</div>" +
                "                    </td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td colspan='3' style='text-align: center; padding-top: 8px;'>" +
                "                      <span style='background: #e0f2fe; color: #0369a1; border-radius: 9999px; padding: 3px 12px; font-size: 11px; font-weight: 700;'>" + nightsStr + "</span>" +
                "                    </td>" +
                "                  </tr>" +
                "                </table>" +
                "              </td>" +
                "            </tr>" +
                "            <tr>" +
                "              <td style='padding-top: 6px;'>" +
                "                <table class='meta-table' width='100%' cellpadding='0' cellspacing='0'>" +
                "                  <tr>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #6b7280; font-size: 14px;'>Ospite:</td>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #09090b; font-weight: 600; text-align: right; font-size: 14px;'>" + guestName + "</td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #6b7280; font-size: 14px;'>Email Ospite:</td>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #0284c7; font-weight: 600; text-align: right; font-size: 14px;'>" + guestEmail + "</td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #6b7280; font-size: 14px;'>Capacità struttura:</td>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #09090b; font-weight: 600; text-align: right; font-size: 14px;'>" + capacity + " ospiti max</td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #6b7280; font-size: 14px;'>Metodo di Pagamento:</td>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #09090b; font-weight: 600; text-align: right; font-size: 14px;'>" + paymentMethodStr + "</td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #6b7280; font-size: 14px;'>Riferimento Transazione:</td>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; text-align: right;'><span style='font-family: monospace; font-size: 12px; background: #f4f4f5; border: 1px solid #e4e4e7; border-radius: 4px; padding: 2px 7px; color: #4b5563;'>" + transactionRef + "</span></td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; color: #6b7280; font-size: 14px;'>Stato Transazione:</td>" +
                "                    <td style='padding: 8px 0; border-bottom: 1px solid #f4f4f5; text-align: right;'><span style='display: inline-block; padding: 3px 10px; background: rgba(16, 185, 129, 0.12); color: #059669; border: 1px solid rgba(16, 185, 129, 0.3); border-radius: 6px; font-weight: 700; font-size: 11px; text-transform: uppercase;'>PAGATO</span></td>" +
                "                  </tr>" +
                "                  <tr>" +
                "                    <td style='padding-top: 14px; color: #9ca3af; font-size: 11px; text-transform: uppercase; font-weight: 700;'>Ricavo Totale Soggiorno</td>" +
                "                    <td style='padding-top: 14px; text-align: right; color: #0284c7; font-size: 24px; font-weight: 800;'>" + totalPriceStr + "</td>" +
                "                  </tr>" +
                "                </table>" +
                "              </td>" +
                "            </tr>" +
                "          </table>" +
                "          <div class='notice-box' style='background: #f0fdf4; border: 1px solid #bbf7d0; border-left: 4px solid #16a34a; padding: 14px 18px; border-radius: 8px; font-size: 13px; color: #15803d; line-height: 1.5; margin-bottom: 26px;'>" +
                "            ✔ Il pagamento è stato riscosso con successo e le date sono state automaticamente riservate nel tuo calendario." +
                "          </div>" +
                "          <div class='btn-container' style='text-align: center; margin: 26px 0 10px;'>" +
                "            <a href='http://localhost:4200/admin' class='btn-primary' style='display: inline-block; background-color: #0284c7; color: #ffffff !important; text-decoration: none; padding: 13px 30px; border-radius: 8px; font-weight: 600; font-size: 14px;'>Apri Pannello Locatore</a>" +
                "          </div>" +
                "        </td>" +
                "      </tr>" +
                "      <tr>" +
                "        <td class='footer' style='background: #f4f4f5; padding: 24px; text-align: center; font-size: 12px; color: #9ca3af; border-top: 1px solid #e4e4e7; line-height: 1.6;'>" +
                "          © 2026 StayHub • Notifica automatica del sistema per i locatori della piattaforma." +
                "        </td>" +
                "      </tr>" +
                "    </table>" +
                "  </div>" +
                "</body>" +
                "</html>";
    }
}
