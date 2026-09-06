package com.stayhub.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.stayhub.backend.entity.Booking;
import com.stayhub.backend.entity.Payment;
import com.stayhub.backend.entity.User;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
public class PdfReceiptService {

    // Palette Colori StayHub
    private static final Color COLOR_PRIMARY = new Color(9, 9, 11);        // #09090b
    private static final Color COLOR_ACCENT = new Color(2, 132, 199);      // #0284c7 Azure
    private static final Color COLOR_MUTED = new Color(107, 114, 128);     // #6b7280
    private static final Color COLOR_BG_LIGHT = new Color(244, 244, 245);  // #f4f4f5
    private static final Color COLOR_BORDER = new Color(228, 228, 231);    // #e4e4e7
    private static final Color COLOR_SUCCESS = new Color(5, 150, 105);     // #059669

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("€ #,##0.00", new DecimalFormatSymbols(Locale.ITALY));

    public void generateReceiptPdf(Booking booking, Payment payment, OutputStream outputStream) throws DocumentException {
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, outputStream);

        document.open();

        // Metadata documento
        document.addTitle("StayHub Ricevuta - Prenotazione #" + booking.getId());
        document.addSubject("Ricevuta di prenotazione StayHub");
        document.addAuthor("StayHub");

        // 1. Header (Brand a sinistra, Dati Ricevuta a destra)
        addHeaderTable(document, booking, payment);

        // Separatore
        addSeparator(document);

        // 2. Dati Ospite & Dati Locatore
        addPartiesTable(document, booking);

        // Spaziatura
        document.add(new Paragraph(" "));

        // 3. Tabella Dettaglio Soggiorno
        addBookingDetailsTable(document, booking);

        // Spaziatura
        document.add(new Paragraph(" "));

        // 4. Box Riepilogo Pagamento (senza costi superflui né scorpori)
        addPaymentSummaryTable(document, booking, payment);

        // Spaziatura
        document.add(new Paragraph(" "));

        // 5. Footer minimalista (senza note legali superflue)
        addFooter(document);

        document.close();
    }

    private void addHeaderTable(Document document, Booking booking, Payment payment) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{60, 40});

        // Colonna Sinistra: Brand essenziale
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(0);

        Paragraph brand = new Paragraph();
        Font stayFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, COLOR_PRIMARY);
        Font hubFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, COLOR_ACCENT);
        brand.add(new Chunk("Stay", stayFont));
        brand.add(new Chunk("Hub", hubFont));
        brand.setSpacingAfter(4);
        leftCell.addElement(brand);

        Font contactFont = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_MUTED);
        leftCell.addElement(new Paragraph("support@stayhub.com", contactFont));

        table.addCell(leftCell);

        // Colonna Destra: Informazioni Ricevuta
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.setPadding(0);

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COLOR_PRIMARY);
        Paragraph docTitle = new Paragraph("RICEVUTA DI PRENOTAZIONE", titleFont);
        docTitle.setAlignment(Element.ALIGN_RIGHT);
        docTitle.setSpacingAfter(4);
        rightCell.addElement(docTitle);

        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_PRIMARY);
        Font metaBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_PRIMARY);

        String paymentIdStr = (payment != null && payment.getId() != null) ? payment.getId().toString() : "0";
        Paragraph numRec = new Paragraph();
        numRec.setAlignment(Element.ALIGN_RIGHT);
        numRec.add(new Chunk("Ricevuta N°: ", metaBold));
        numRec.add(new Chunk("REC-" + booking.getId() + "-" + paymentIdStr, metaFont));
        rightCell.addElement(numRec);

        Paragraph dateRec = new Paragraph();
        dateRec.setAlignment(Element.ALIGN_RIGHT);
        dateRec.add(new Chunk("Data: ", metaBold));
        dateRec.add(new Chunk(LocalDate.now().format(DATE_FORMATTER), metaFont));
        rightCell.addElement(dateRec);

        Paragraph statusRec = new Paragraph();
        statusRec.setAlignment(Element.ALIGN_RIGHT);
        statusRec.add(new Chunk("Stato: ", metaBold));
        Font statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_SUCCESS);
        statusRec.add(new Chunk("CONFERMATA", statusFont));
        rightCell.addElement(statusRec);

        table.addCell(rightCell);
        document.add(table);
    }

    private void addSeparator(Document document) throws DocumentException {
        LineSeparator line = new LineSeparator();
        line.setLineColor(COLOR_BORDER);
        line.setLineWidth(1);
        line.setPercentage(100);
        Paragraph p = new Paragraph(" ");
        p.setSpacingBefore(6);
        p.setSpacingAfter(6);
        document.add(p);
        document.add(line);
        document.add(new Paragraph(" "));
    }

    private void addPartiesTable(Document document, Booking booking) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{50, 50});

        Font sectionHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_ACCENT);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, COLOR_MUTED);
        Font valFont = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_PRIMARY);

        // Cella Ospite
        PdfPCell guestCell = new PdfPCell();
        guestCell.setBackgroundColor(COLOR_BG_LIGHT);
        guestCell.setBorderColor(COLOR_BORDER);
        guestCell.setPadding(10);

        guestCell.addElement(new Paragraph("DATI OSPITE", sectionHeaderFont));
        guestCell.addElement(new Paragraph("Nome Completo:", labelFont));
        String guestName = "Ospite";
        String guestEmail = "-";
        if (booking.getUser() != null) {
            String fName = booking.getUser().getFirstName() != null ? booking.getUser().getFirstName().trim() : "";
            String lName = booking.getUser().getLastName() != null ? booking.getUser().getLastName().trim() : "";
            String full = (fName + " " + lName).trim();
            if (!full.isEmpty()) {
                guestName = full;
            } else if (booking.getUser().getEmail() != null) {
                guestName = booking.getUser().getEmail();
            }
            if (booking.getUser().getEmail() != null && !booking.getUser().getEmail().trim().isEmpty()) {
                guestEmail = booking.getUser().getEmail().trim();
            }
        }
        guestCell.addElement(new Paragraph(guestName, valFont));

        guestCell.addElement(new Paragraph("Email:", labelFont));
        guestCell.addElement(new Paragraph(guestEmail, valFont));

        table.addCell(guestCell);

        // Cella Locatore
        PdfPCell hostCell = new PdfPCell();
        hostCell.setBackgroundColor(COLOR_BG_LIGHT);
        hostCell.setBorderColor(COLOR_BORDER);
        hostCell.setPadding(10);

        hostCell.addElement(new Paragraph("DATI LOCATORE", sectionHeaderFont));
        hostCell.addElement(new Paragraph("Locatore:", labelFont));
        String hostName = "Locatore StayHub";
        String hostEmail = "-";
        if (booking.getRoom() != null && booking.getRoom().getOwner() != null) {
            User owner = booking.getRoom().getOwner();
            String fName = owner.getFirstName() != null ? owner.getFirstName().trim() : "";
            String lName = owner.getLastName() != null ? owner.getLastName().trim() : "";
            String full = (fName + " " + lName).trim();
            if (!full.isEmpty()) {
                hostName = full;
            } else if (owner.getEmail() != null) {
                hostName = owner.getEmail();
            }
            if (owner.getEmail() != null && !owner.getEmail().trim().isEmpty()) {
                hostEmail = owner.getEmail().trim();
            }
        }
        hostCell.addElement(new Paragraph(hostName, valFont));

        hostCell.addElement(new Paragraph("Email Locatore:", labelFont));
        hostCell.addElement(new Paragraph(hostEmail, valFont));

        table.addCell(hostCell);
        document.add(table);
    }

    private void addBookingDetailsTable(Document document, Booking booking) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{34, 17, 17, 12, 20});

        Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, COLOR_PRIMARY);
        Font tdFont = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_PRIMARY);

        // Intestazioni
        String[] headers = {"Alloggio", "Check-in", "Check-out", "Notti", "Totale"};
        for (String header : headers) {
            PdfPCell th = new PdfPCell(new Phrase(header, thFont));
            th.setBackgroundColor(COLOR_BG_LIGHT);
            th.setBorderColor(COLOR_BORDER);
            th.setPadding(8);
            if (header.equals("Notti") || header.equals("Totale")) {
                th.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            table.addCell(th);
        }

        // Calcolo notti
        long nights = ChronoUnit.DAYS.between(booking.getCheckIn(), booking.getCheckOut());
        if (nights <= 0) nights = 1;

        // Dati riga
        String roomName = booking.getRoom() != null ? booking.getRoom().getName() : "Alloggio";
        PdfPCell c1 = new PdfPCell(new Phrase(roomName, tdFont));
        c1.setPadding(8);
        c1.setBorderColor(COLOR_BORDER);
        table.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(booking.getCheckIn().format(DATE_FORMATTER), tdFont));
        c2.setPadding(8);
        c2.setBorderColor(COLOR_BORDER);
        table.addCell(c2);

        PdfPCell c3 = new PdfPCell(new Phrase(booking.getCheckOut().format(DATE_FORMATTER), tdFont));
        c3.setPadding(8);
        c3.setBorderColor(COLOR_BORDER);
        table.addCell(c3);

        PdfPCell c4 = new PdfPCell(new Phrase(String.valueOf(nights), tdFont));
        c4.setPadding(8);
        c4.setBorderColor(COLOR_BORDER);
        c4.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c4);

        PdfPCell c5 = new PdfPCell(new Phrase(CURRENCY_FORMAT.format(booking.getTotalPrice()), tdFont));
        c5.setPadding(8);
        c5.setBorderColor(COLOR_BORDER);
        c5.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c5);

        document.add(table);
    }

    private void addPaymentSummaryTable(Document document, Booking booking, Payment payment) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{60, 40});

        // Cella Sinistra: Info Transazione essenziali
        PdfPCell left = new PdfPCell();
        left.setBorderColor(COLOR_BORDER);
        left.setPadding(10);

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_PRIMARY);
        Font valFont = FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_MUTED);
        Font valBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, COLOR_PRIMARY);

        left.addElement(new Paragraph("DETTAGLI PAGAMENTO", titleFont));

        Paragraph pMethod = new Paragraph();
        pMethod.add(new Chunk("Metodo: ", valFont));
        String method = (payment != null && payment.getPaymentMethod() != null) ? payment.getPaymentMethod().name().replace("_", " ") : "CARTA DI CREDITO";

        String ref = (payment != null && payment.getTransactionReference() != null) ? payment.getTransactionReference() : "TX-STAYHUB-" + booking.getId();
        String authCode = null;
        if (ref != null && ref.startsWith("TX-CRD-")) {
            String[] parts = ref.split("-");
            if (parts.length >= 5) {
                String last4 = parts[3];
                authCode = parts[4];
                method = "CARTA DI CREDITO (**** " + last4 + ")";
            }
        }
        pMethod.add(new Chunk(method, valBold));
        left.addElement(pMethod);

        Paragraph pRef = new Paragraph();
        pRef.add(new Chunk("Transazione: ", valFont));
        pRef.add(new Chunk(ref, valBold));
        left.addElement(pRef);

        if (authCode != null) {
            Paragraph pAuth = new Paragraph();
            pAuth.add(new Chunk("Codice Autorizzazione (Auth): ", valFont));
            pAuth.add(new Chunk(authCode, valBold));
            left.addElement(pAuth);
        }

        table.addCell(left);

        // Cella Destra: Totale Pagato Evidenziato (pulito, senza IVA né voci superflue)
        PdfPCell right = new PdfPCell();
        right.setBackgroundColor(COLOR_BG_LIGHT);
        right.setBorderColor(COLOR_BORDER);
        right.setPadding(12);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Font totLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_MUTED);
        Paragraph pTotLbl = new Paragraph("TOTALE PAGATO", totLabel);
        pTotLbl.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(pTotLbl);

        Font totValue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, COLOR_ACCENT);
        Paragraph pTotVal = new Paragraph(CURRENCY_FORMAT.format(booking.getTotalPrice()), totValue);
        pTotVal.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(pTotVal);

        table.addCell(right);
        document.add(table);
    }

    private void addFooter(Document document) throws DocumentException {
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_MUTED);
        Paragraph footer = new Paragraph("StayHub • support@stayhub.com", footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(24);
        document.add(footer);
    }
}
