package com.gadaaistar.admin.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.gadaaistar.common.ProductCategory;
import com.gadaaistar.common.ProductStatus;
import com.gadaaistar.persistence.catalog.Inventory;
import com.gadaaistar.persistence.catalog.InventoryRepository;
import com.gadaaistar.persistence.catalog.Product;
import com.gadaaistar.persistence.catalog.ProductRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.MediaType;

import org.jboss.logging.Logger;

/**
 * Bulk product import / export via CSV.
 *
 * <h4>Export</h4>
 * Generates a CSV with one row per product. Columns match the
 * {@link Product} entity's editable fields. The header row is always
 * present so the same file can be re-imported after editing.
 *
 * <h4>Import</h4>
 * Parses a CSV with the same column layout. For each row:
 * <ul>
 *   <li>If the SKU already exists → update the product's fields (PATCH-style
 *       — only non-empty cells are applied).</li>
 *   <li>If the SKU is new → create a product with DRAFT status and seed a
 *       zero-quantity inventory row.</li>
 * </ul>
 *
 * <h4>CSV format</h4>
 * <pre>
 * sku,slug,name,shortDescription,description,category,status,weightKg,hsCode,originCountry,baseCurrency,basePrice,costPrice,minOrderQuantity,packagingNotes
 * ETH-YIRGACHEFFE-01,eth-yirgacheffe-01,Yirgacheffe Single Origin,,Floral and citrus...,RAW_COFFEE,DRAFT,2.5,0901.21,ET,USD,18.50,12.00,1,50kg jute bags
 * </pre>
 *
 * Design choices:
 * <ul>
 *   <li>No external CSV library — the product schema is simple enough for
 *       manual parsing. Quoted fields with embedded commas are handled.</li>
 *   <li>Import is all-or-nothing per row: a row with a bad enum value or
 *       missing required field is skipped and reported in the summary, but
 *       valid rows still process. This is intentional — admins want partial
 *       success, not a rollback of 200 good rows because row 47 had a typo.</li>
 *   <li>Empty cells on update are treated as "leave unchanged". The literal
 *       string {@code !CLEAR} can be used to set a nullable field to null.</li>
 * </ul>
 */
@ApplicationScoped
public class ProductCsvService {

    private static final Logger LOG = Logger.getLogger(ProductCsvService.class);

    static final String[] COLUMNS = {
            "sku", "slug", "name", "shortDescription", "description",
            "category", "status", "weightKg", "hsCode", "originCountry",
            "baseCurrency", "basePrice", "costPrice", "minOrderQuantity",
            "packagingNotes"
    };

    @Inject ProductRepository    products;
    @Inject InventoryRepository  inventories;

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    public String exportCsv() {
        var all = products.findAll().list();
        var sw = new StringWriter();

        // Header
        sw.append(String.join(",", COLUMNS)).append("\r\n");

        for (var p : all) {
            var row = new String[COLUMNS.length];
            row[0]  = csv(p.sku);
            row[1]  = csv(p.slug);
            row[2]  = csv(p.name);
            row[3]  = csv(p.shortDescription);
            row[4]  = csv(p.description);
            row[5]  = p.category == null ? "" : p.category.name();
            row[6]  = p.status == null ? "" : p.status.name();
            row[7]  = p.weightKg == null ? "" : p.weightKg.toPlainString();
            row[8]  = csv(p.hsCode);
            row[9]  = csv(p.originCountry);
            row[10] = csv(p.baseCurrency);
            row[11] = p.basePrice == null ? "" : p.basePrice.toPlainString();
            row[12] = p.costPrice == null ? "" : p.costPrice.toPlainString();
            row[13] = p.minOrderQuantity == null ? "" : p.minOrderQuantity.toPlainString();
            row[14] = csv(p.packagingNotes);
            sw.append(String.join(",", row)).append("\r\n");
        }

        LOG.infof("Exported %d products to CSV", all.size());
        return sw.toString();
    }

    // -------------------------------------------------------------------------
    // Import
    // -------------------------------------------------------------------------

    public ImportResult importCsv(String csvText) {
        var result = new ImportResult();
        var rows = parseCsv(csvText);

        if (rows.isEmpty()) {
            result.errors.add("CSV file is empty or has no data rows.");
            return result;
        }

        // Validate header
        var header = rows.get(0);
        if (header.length < COLUMNS.length) {
            result.errors.add("CSV header has " + header.length
                    + " columns, expected at least " + COLUMNS.length + ".");
            return result;
        }

        for (int i = 1; i < rows.size(); i++) {
            var row = rows.get(i);
            try {
                processRow(row, result);
            } catch (Exception e) {
                result.errors.add("Row " + (i + 1) + ": " + e.getMessage());
            }
        }

        LOG.infof("CSV import complete: %d created, %d updated, %d errors",
                result.created, result.updated, result.errors.size());
        return result;
    }

    @Transactional
    void processRow(String[] row, ImportResult result) {
        var sku = val(row, 0);
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU is required");
        }

        var existing = products.findBySku(sku);
        if (existing.isPresent()) {
            updateProduct(existing.get(), row);
            result.updated++;
        } else {
            createProduct(sku, row);
            result.created++;
        }
    }

    private void createProduct(String sku, String[] row) {
        var name = val(row, 2);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required for new product (SKU=" + sku + ")");
        }

        var p = new Product();
        p.sku = sku;
        p.slug = val(row, 1) != null ? val(row, 1) : slugify(name);
        p.name = name;
        p.shortDescription = val(row, 3);
        p.description = val(row, 4);
        p.category = parseEnum(val(row, 5), ProductCategory.class, ProductCategory.RAW_COFFEE);
        p.status = parseEnum(val(row, 6), ProductStatus.class, ProductStatus.DRAFT);
        p.weightKg = parseDecimal(val(row, 7));
        p.hsCode = val(row, 8);
        p.originCountry = val(row, 9) != null ? val(row, 9) : "ET";
        p.baseCurrency = val(row, 10) != null ? val(row, 10) : "USD";
        p.basePrice = parseDecimal(val(row, 11));
        p.costPrice = parseDecimal(val(row, 12));
        p.minOrderQuantity = parseDecimal(val(row, 13));
        if (p.minOrderQuantity == null) p.minOrderQuantity = BigDecimal.ONE;
        p.packagingNotes = val(row, 14);
        products.persist(p);

        var inv = new Inventory();
        inv.product = p;
        inv.quantity = BigDecimal.ZERO;
        inv.reservedQuantity = BigDecimal.ZERO;
        inventories.persist(inv);
    }

    private void updateProduct(Product p, String[] row) {
        var slug = val(row, 1);
        if (slug != null && !slug.isBlank()) p.slug = slug;

        var name = val(row, 2);
        if (name != null && !name.isBlank()) p.name = name;

        var shortDesc = val(row, 3);
        if (shortDesc != null) p.shortDescription = "!CLEAR".equals(shortDesc) ? null : shortDesc;

        var desc = val(row, 4);
        if (desc != null) p.description = "!CLEAR".equals(desc) ? null : desc;

        var cat = val(row, 5);
        if (cat != null && !cat.isBlank()) {
            p.category = parseEnum(cat, ProductCategory.class, p.category);
        }

        var status = val(row, 6);
        if (status != null && !status.isBlank()) {
            p.status = parseEnum(status, ProductStatus.class, p.status);
        }

        var weight = val(row, 7);
        if (weight != null && !weight.isBlank()) p.weightKg = parseDecimal(weight);

        var hsCode = val(row, 8);
        if (hsCode != null) p.hsCode = "!CLEAR".equals(hsCode) ? null : hsCode;

        var origin = val(row, 9);
        if (origin != null && !origin.isBlank()) p.originCountry = origin;

        var currency = val(row, 10);
        if (currency != null && !currency.isBlank()) p.baseCurrency = currency;

        var basePrice = val(row, 11);
        if (basePrice != null && !basePrice.isBlank()) p.basePrice = parseDecimal(basePrice);

        var costPrice = val(row, 12);
        if (costPrice != null && !costPrice.isBlank()) p.costPrice = parseDecimal(costPrice);

        var moq = val(row, 13);
        if (moq != null && !moq.isBlank()) p.minOrderQuantity = parseDecimal(moq);

        var notes = val(row, 14);
        if (notes != null) p.packagingNotes = "!CLEAR".equals(notes) ? null : notes;
    }

    // -------------------------------------------------------------------------
    // CSV parsing helpers
    // -------------------------------------------------------------------------

    /**
     * Minimal RFC-4180-ish CSV parser. Handles quoted fields, embedded commas,
     * and doubled double-quotes for escaping.
     */
    static List<String[]> parseCsv(String text) {
        var rows = new ArrayList<String[]>();
        try (var reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                rows.add(parseLine(line));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read CSV: " + e.getMessage(), e);
        }
        return rows;
    }

    static String[] parseLine(String line) {
        var fields = new ArrayList<String>();
        var sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }

    /** Quote a field for CSV output if it contains commas, quotes, or newlines. */
    static String csv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Get a trimmed value from a row index, returning null for blank strings. */
    static String val(String[] row, int idx) {
        if (idx >= row.length) return null;
        var v = row[idx].trim();
        return v.isEmpty() ? null : v;
    }

    static BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid decimal value: " + s);
        }
    }

    static <E extends Enum<E>> E parseEnum(String s, Class<E> enumClass, E defaultValue) {
        if (s == null || s.isBlank()) return defaultValue;
        try {
            return Enum.valueOf(enumClass, s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + enumClass.getSimpleName() + " value: " + s);
        }
    }

    static String slugify(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    public static class ImportResult {
        public int created;
        public int updated;
        public final List<String> errors = new ArrayList<>();

        public boolean isSuccess() {
            return errors.isEmpty();
        }
    }
}
