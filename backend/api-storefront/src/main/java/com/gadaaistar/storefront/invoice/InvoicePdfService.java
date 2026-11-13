package com.gadaaistar.storefront.invoice;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import com.gadaaistar.persistence.customer.CustomerAddress;
import com.gadaaistar.persistence.order.CheckoutOrder;
import com.gadaaistar.persistence.order.OrderItem;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.draw.LineSeparator;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Renders the customer-downloadable invoice PDF for a paid order.
 *
 * <p>Layout follows the house style of the official invoice template:
 * company block + "OFFICIAL INVOICE" title, invoice meta (number, dates,
 * status), customer details, a line-item table, totals, a payment-details
 * block naming the actual gateway that collected the money (MPGS,
 * CyberSource, …), a QR code that points at the public order-tracking page,
 * and a closing disclaimer.
 *
 * <p>Generation is on demand — nothing is persisted. All amounts come from
 * the snapshotted {@code checkout_order} / {@code order_item} rows, so the
 * document stays correct even if catalog prices change later.
 */
@ApplicationScoped
public class InvoicePdfService {

    private static final Logger LOG = Logger.getLogger(InvoicePdfService.class);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    // Palette — charcoal + brand gold, matching the storefront accent.
    private static final Color INK       = new Color(0x21, 0x21, 0x21);
    private static final Color MUTED     = new Color(0x66, 0x66, 0x66);
    private static final Color GOLD      = new Color(0xB8, 0x86, 0x2D);
    private static final Color RULE      = new Color(0xDD, 0xDD, 0xDD);
    private static final Color HEAD_FILL = new Color(0xF6, 0xF1, 0xE8);

    private static final Font F_COMPANY  = new Font(Font.HELVETICA, 16, Font.BOLD,  INK);
    private static final Font F_TITLE    = new Font(Font.HELVETICA, 22, Font.BOLD,  GOLD);
    private static final Font F_SECTION  = new Font(Font.HELVETICA, 10, Font.BOLD,  GOLD);
    private static final Font F_LABEL    = new Font(Font.HELVETICA, 9,  Font.NORMAL, MUTED);
    private static final Font F_VALUE    = new Font(Font.HELVETICA, 10, Font.NORMAL, INK);
    private static final Font F_VALUE_B  = new Font(Font.HELVETICA, 10, Font.BOLD,  INK);
    private static final Font F_TABLE_H  = new Font(Font.HELVETICA, 9,  Font.BOLD,  INK);
    private static final Font F_TOTAL_B  = new Font(Font.HELVETICA, 12, Font.BOLD,  INK);
    private static final Font F_SMALL    = new Font(Font.HELVETICA, 8,  Font.NORMAL, MUTED);

    @ConfigProperty(name = "gadaa.company.legal-name", defaultValue = "Gadaa Istar Business Group S.C.")
    String companyLegalName;

    // Optional<>: application.properties defines these as empty strings,
    // which SmallRye converts to null — a plain String injection would fail
    // startup with "empty String considered null".
    @ConfigProperty(name = "gadaa.company.tin")
    Optional<String> companyTin;

    @ConfigProperty(name = "gadaa.company.vat-number")
    Optional<String> companyVat;

    @ConfigProperty(name = "gadaa.frontend.storefront-url", defaultValue = "https://gedaaistar.et")
    String storefrontBaseUrl;

    /**
     * Render the invoice for a paid order. Must be called while the order's
     * lazy associations (items, customer, addresses) can still be loaded —
     * i.e. inside the request's persistence context.
     */
    public byte[] render(CheckoutOrder order) {
        var out = new ByteArrayOutputStream(64 * 1024);
        var doc = new Document(PageSize.A4, 50, 50, 56, 56);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            addHeader(doc, order);
            addCustomerBlock(doc, order);
            addItemsTable(doc, order);
            addTotals(doc, order);
            addPaymentBlock(doc, order);
            addVerification(doc, order);
            addDisclaimer(doc);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            try { if (doc.isOpen()) doc.close(); } catch (Exception ignored) {}
            throw new IllegalStateException("Could not render invoice PDF for order " + order.uuid, e);
        }
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private void addHeader(Document doc, CheckoutOrder order) throws Exception {
        var table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{60, 40});

        var left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Paragraph(companyLegalName, F_COMPANY));
        companyTin.filter(s -> !s.isBlank())
                .ifPresent(tin -> left.addElement(new Paragraph("TIN: " + tin, F_SMALL)));
        companyVat.filter(s -> !s.isBlank())
                .ifPresent(vat -> left.addElement(new Paragraph("VAT: " + vat, F_SMALL)));
        table.addCell(left);

        var right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        var title = new Paragraph("OFFICIAL INVOICE", F_TITLE);
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);
        table.addCell(right);

        doc.add(table);

        var rule = new LineSeparator(1.5f, 100, GOLD, Element.ALIGN_LEFT, -2);
        var spacer = new Paragraph(" ");
        spacer.setSpacingAfter(2);
        doc.add(spacer);
        doc.add(rule);

        // Invoice meta — two columns of label/value pairs.
        var meta = new PdfPTable(4);
        meta.setWidthPercentage(100);
        meta.setWidths(new float[]{16, 34, 16, 34});
        meta.setSpacingBefore(8);
        meta.setSpacingAfter(6);

        metaRow(meta, "Invoice #:", order.invoiceNumber,
                      "Status:", statusLabel(order));
        metaRow(meta, "Order Date:", DATE_FMT.format(order.placedAt),
                      "Payment Date:", order.paidAt == null ? "—" : DATE_FMT.format(order.paidAt));

        doc.add(meta);
    }

    private void addCustomerBlock(Document doc, CheckoutOrder order) throws Exception {
        doc.add(new Paragraph("CUSTOMER DETAILS", F_SECTION));

        var table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(10);

        var customer = order.customer;
        var bill = new Paragraph();
        bill.add(new Phrase("Bill to\n", F_LABEL));
        bill.add(new Phrase(customer.displayName() + "\n", F_VALUE_B));
        if (customer.companyName != null && !customer.companyName.isBlank()
                && !customer.companyName.equals(customer.displayName())) {
            bill.add(new Phrase(customer.companyName + "\n", F_VALUE));
        }
        bill.add(new Phrase(customer.email + "\n", F_VALUE));
        if (customer.phone != null && !customer.phone.isBlank()) {
            bill.add(new Phrase(customer.phone + "\n", F_VALUE));
        }
        if (customer.companyTin != null && !customer.companyTin.isBlank()) {
            bill.add(new Phrase("TIN: " + customer.companyTin + "\n", F_VALUE));
        }
        appendAddress(bill, order.billToAddress);

        var ship = new Paragraph();
        ship.add(new Phrase("Ship to\n", F_LABEL));
        appendAddress(ship, order.shipToAddress);
        if (order.shippingMethodName != null && !order.shippingMethodName.isBlank()) {
            ship.add(new Phrase("\nShipping method: " + order.shippingMethodName, F_VALUE));
        }

        table.addCell(borderless(bill));
        table.addCell(borderless(ship));
        doc.add(table);
    }

    private void addItemsTable(Document doc, CheckoutOrder order) throws Exception {
        doc.add(new Paragraph("ITEMS", F_SECTION));

        var table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{6, 40, 14, 12, 14, 14});
        table.setSpacingBefore(4);
        table.setHeaderRows(1);

        headerCell(table, "#");
        headerCell(table, "PRODUCT");
        headerCell(table, "SKU");
        headerCell(table, "QTY");
        headerCell(table, "UNIT PRICE");
        headerCell(table, "AMOUNT");

        int n = 0;
        for (OrderItem item : order.items) {
            n++;
            bodyCell(table, String.valueOf(n), Element.ALIGN_LEFT);

            var name = item.name;
            if (item.variantName != null && !item.variantName.isBlank()) {
                name += " — " + item.variantName;
            }
            bodyCell(table, name, Element.ALIGN_LEFT);
            bodyCell(table, item.sku, Element.ALIGN_LEFT);
            bodyCell(table, qty(item.quantity), Element.ALIGN_RIGHT);
            bodyCell(table, money(order.currency, item.unitPrice), Element.ALIGN_RIGHT);
            bodyCell(table, money(order.currency, item.lineTotal), Element.ALIGN_RIGHT);
        }

        doc.add(table);
    }

    private void addTotals(Document doc, CheckoutOrder order) throws Exception {
        var table = new PdfPTable(2);
        table.setWidthPercentage(45);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setWidths(new float[]{55, 45});
        table.setSpacingBefore(8);

        totalRow(table, "Subtotal:", money(order.currency, order.subtotal), false);
        if (order.discountTotal != null && order.discountTotal.signum() > 0) {
            var label = "Discount"
                    + (order.couponCode != null && !order.couponCode.isBlank()
                            ? " (" + order.couponCode + ")" : "") + ":";
            totalRow(table, label, "−" + money(order.currency, order.discountTotal), false);
        }
        totalRow(table, "Shipping:", money(order.currency, order.shippingTotal), false);
        totalRow(table, "Tax / VAT:", money(order.currency, order.taxTotal), false);

        var totalLabel = new PdfPCell(new Phrase("Total Paid:", F_TOTAL_B));
        var totalValue = new PdfPCell(new Phrase(money(order.currency, order.grandTotal), F_TOTAL_B));
        for (var cell : new PdfPCell[]{totalLabel, totalValue}) {
            cell.setBorder(Rectangle.TOP);
            cell.setBorderColor(GOLD);
            cell.setPaddingTop(6);
            cell.setPaddingBottom(2);
        }
        totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(totalLabel);
        table.addCell(totalValue);

        doc.add(table);
    }

    private void addPaymentBlock(Document doc, CheckoutOrder order) throws Exception {
        doc.add(spacer(14));
        doc.add(new Paragraph("PAYMENT DETAILS", F_SECTION));

        var table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{20, 30, 20, 30});
        table.setSpacingBefore(4);

        metaRow(table, "Payment Method:", providerLabel(order),
                      "Paid At:", order.paidAt == null ? "—" : DATETIME_FMT.format(order.paidAt));
        metaRow(table, "Transaction Ref:", order.paymentReference == null ? "—" : order.paymentReference,
                      "Currency:", order.currency);

        doc.add(table);
    }

    private void addVerification(Document doc, CheckoutOrder order) throws Exception {
        doc.add(spacer(14));
        doc.add(new Paragraph("DIGITAL VERIFICATION", F_SECTION));

        var trackUrl = storefrontBaseUrl + "/track-order";

        var table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{14, 86});
        table.setSpacingBefore(4);

        // QR code — links to the public tracking page where the invoice number
        // + customer email verify the order. Wrapped so a QR failure never
        // kills invoice generation.
        PdfPCell qrCell;
        try {
            var qr = new QRCodeWriter().encode(trackUrl, BarcodeFormat.QR_CODE, 140, 140);
            var png = new ByteArrayOutputStream(8 * 1024);
            MatrixToImageWriter.writeToStream(qr, "PNG", png);
            var image = Image.getInstance(png.toByteArray());
            image.scaleToFit(64, 64);
            qrCell = new PdfPCell(image, false);
        } catch (Exception e) {
            LOG.warnf("QR generation failed for invoice %s — continuing without it", order.invoiceNumber);
            qrCell = new PdfPCell(new Phrase(" "));
        }
        qrCell.setBorder(Rectangle.NO_BORDER);
        qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        qrCell.setRowspan(2);
        table.addCell(qrCell);

        var text = new Paragraph();
        text.add(new Phrase(
                "Scan the QR code or visit the link below and enter the invoice number "
                        + "with your account email to verify this order online.\n", F_VALUE));
        text.add(new Phrase(trackUrl + "\n", F_VALUE_B));
        text.add(new Phrase("Reference: " + order.invoiceNumber, F_SMALL));
        var textCell = new PdfPCell(text);
        textCell.setBorder(Rectangle.NO_BORDER);
        textCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(textCell);

        doc.add(table);
    }

    private void addDisclaimer(Document doc) throws Exception {
        doc.add(spacer(16));
        var rule = new LineSeparator(0.75f, 100, RULE, Element.ALIGN_LEFT, -2);
        doc.add(rule);
        var p = new Paragraph(
                "Thank you for your business. This invoice was generated electronically and is "
                        + "valid without a signature. Goods remain the property of "
                        + companyLegalName + " until delivered under the agreed shipping terms. "
                        + "For questions about this invoice please contact us quoting the invoice number.",
                F_SMALL);
        p.setSpacingBefore(6);
        doc.add(p);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void metaRow(PdfPTable table, String l1, String v1, String l2, String v2) {
        table.addCell(borderless(new Phrase(l1, F_LABEL)));
        table.addCell(borderless(new Phrase(v1, F_VALUE_B)));
        table.addCell(borderless(new Phrase(l2, F_LABEL)));
        table.addCell(borderless(new Phrase(v2, F_VALUE)));
    }

    private void headerCell(PdfPTable table, String text) {
        var cell = new PdfPCell(new Phrase(text, F_TABLE_H));
        cell.setBackgroundColor(HEAD_FILL);
        cell.setBorderColor(RULE);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void bodyCell(PdfPTable table, String text, int align) {
        var cell = new PdfPCell(new Phrase(text, F_VALUE));
        cell.setBorderColor(RULE);
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void totalRow(PdfPTable table, String label, String value, boolean bold) {
        var l = new PdfPCell(new Phrase(label, F_LABEL));
        var v = new PdfPCell(new Phrase(value, F_VALUE));
        for (var cell : new PdfPCell[]{l, v}) {
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPaddingBottom(2);
        }
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(l);
        table.addCell(v);
    }

    private PdfPCell borderless(Element content) {
        var cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.addElement(content);
        return cell;
    }

    private PdfPCell borderless(Phrase phrase) {
        var cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private Paragraph spacer(float height) {
        var p = new Paragraph(" ");
        p.setSpacingBefore(height);
        return p;
    }

    private void appendAddress(Paragraph p, CustomerAddress a) {
        if (a == null) {
            p.add(new Phrase("—\n", F_VALUE));
            return;
        }
        if (a.fullName != null && !a.fullName.isBlank()) {
            p.add(new Phrase(a.fullName + "\n", F_VALUE));
        }
        if (a.company != null && !a.company.isBlank()) {
            p.add(new Phrase(a.company + "\n", F_VALUE));
        }
        var line = new StringBuilder(a.streetLine1 == null ? "" : a.streetLine1);
        if (a.streetLine2 != null && !a.streetLine2.isBlank()) {
            line.append(", ").append(a.streetLine2);
        }
        p.add(new Phrase(line + "\n", F_VALUE));
        var city = new StringBuilder(a.city == null ? "" : a.city);
        if (a.region != null && !a.region.isBlank()) {
            city.append(", ").append(a.region);
        }
        if (a.postalCode != null && !a.postalCode.isBlank()) {
            city.append(" ").append(a.postalCode);
        }
        p.add(new Phrase(city + "\n", F_VALUE));
        p.add(new Phrase(a.countryCode + "\n", F_VALUE));
    }

    private String statusLabel(CheckoutOrder order) {
        return order.status.name().replace('_', ' ');
    }

    /**
     * Human-readable gateway name — this is the actual provider that
     * confirmed the money ({@code paymentProvider}), falling back to the
     * method the customer picked. "MPGS" stays "MPGS", never a generic
     * "card" label.
     */
    private String providerLabel(CheckoutOrder order) {
        var raw = order.paymentProvider != null && !order.paymentProvider.isBlank()
                ? order.paymentProvider
                : order.paymentMethod;
        if (raw == null || raw.isBlank()) {
            return "—";
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "MPGS"        -> "MPGS — Mastercard Payment Gateway Services";
            case "CYBERSOURCE" -> "CyberSource";
            default           -> raw;
        };
    }

    private String qty(BigDecimal q) {
        return q.stripTrailingZeros().toPlainString();
    }

    private String money(String currency, BigDecimal amount) {
        if (amount == null) return "—";
        return String.format(Locale.US, "%,.2f %s",
                amount.setScale(2, RoundingMode.HALF_UP), currency);
    }
}
