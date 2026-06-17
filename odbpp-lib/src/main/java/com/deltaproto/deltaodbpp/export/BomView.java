package com.deltaproto.deltaodbpp.export;

import com.deltaproto.deltaodbpp.model.Bom;
import com.deltaproto.deltaodbpp.model.BomItem;
import com.deltaproto.deltaodbpp.model.Component;
import com.deltaproto.deltaodbpp.model.Job;
import com.deltaproto.deltaodbpp.model.Layer;
import com.deltaproto.deltaodbpp.model.MirrorType;
import com.deltaproto.deltaodbpp.model.PropertyRecord;
import com.deltaproto.deltaodbpp.model.Step;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a combined BOM + centroid table for the viewer.
 *
 * <p>Each row corresponds to a part (one BOM line, or — for components that
 * have no BOM entry — a synthetic row keyed by part name). Each row carries
 * its full refdes list, and every refdes entry carries its centroid (mm),
 * rotation, side, mirror and package — so the UI can show centroid details
 * on hover without a second request.
 */
public final class BomView {

    private BomView() {}

    public static final class Row {
        /** BOM item number, or {@code null} when the row is a synthetic group of orphan components. */
        public Integer item;
        public String partNumber;       // CPN / part_name from the CMP record
        public String packageName;      // PKG
        public String manufacturer;     // VND
        public String mpn;
        public String ipn;
        public String description;
        public int quantity;
        public List<RefdesEntry> refdesList = new ArrayList<>();
        /** {@code true} when this row was synthesised from components with no BOM line. */
        public boolean orphan;
    }

    public static final class RefdesEntry {
        public String refdes;
        public String side;             // TOP / BOTTOM / "" if unknown
        public Double xMm;
        public Double yMm;
        public Double rotation;
        public String mirror;           // N / M
        public String packageName;
    }

    public static List<Row> build(Job job) {
        if (job == null || job.getSteps() == null || job.getSteps().isEmpty()) return List.of();
        Step step = job.getSteps().values().iterator().next();
        return build(job, step);
    }

    public static List<Row> build(Job job, Step step) {
        if (step == null) return List.of();

        // Coordinates are normalised to mm at parse time (StepParser threads the
        // step's UNITS through to ComponentsParser et al.), so no conversion here.
        LayerSideClassifier classifier = new LayerSideClassifier(job != null ? job.getMatrix() : null);

        // Group components by their partName (matches BomItem.cpn).
        Map<String, List<Component>> componentsByPart = new LinkedHashMap<>();
        for (Map.Entry<String, Layer> entry : step.getLayersByName().entrySet()) {
            Layer layer = entry.getValue();
            if (layer.getComponents() == null || layer.getComponents().getComponents() == null) continue;
            String layerSide = classifier.sideOf(entry.getKey()).name();
            for (Component c : layer.getComponents().getComponents()) {
                String key = c.getPartName() != null ? c.getPartName() : "(no part)";
                componentsByPart.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
                // Annotate the layer side via a parallel map below — see refdes builder.
                // (We re-derive side per refdes below using the same classifier.)
            }
        }

        // Build a side-by-refdes map so we can fill RefdesEntry.side regardless of which BOM
        // group we end up grouping the component under.
        Map<String, String> sideByRefdes = new LinkedHashMap<>();
        for (Map.Entry<String, Layer> entry : step.getLayersByName().entrySet()) {
            Layer layer = entry.getValue();
            if (layer.getComponents() == null || layer.getComponents().getComponents() == null) continue;
            String side = classifier.sideOf(entry.getKey()).name();
            for (Component c : layer.getComponents().getComponents()) {
                if (c.getCompName() != null) sideByRefdes.put(c.getCompName(), side);
            }
        }

        List<Row> rows = new ArrayList<>();

        // 1) Walk the BOM and pick up matching components.
        Bom bom = step.getBom();
        if (bom != null && bom.getItems() != null) {
            for (BomItem item : bom.getItems()) {
                Row row = new Row();
                row.item = item.getItemNumber();
                row.partNumber = item.getCpn();
                row.packageName = item.getPkg();
                row.manufacturer = item.getVnd();
                row.mpn = item.getMpn();
                row.ipn = item.getIpn();
                row.description = joinDescriptions(item.getDescriptions());
                row.quantity = item.getQuantity();

                List<Component> matches = componentsByPart.remove(item.getCpn());
                if (matches != null) {
                    for (Component c : matches) {
                        row.refdesList.add(toRefdesEntry(c, sideByRefdes));
                    }
                }
                // Fall back to BomItem.quantity when component join produced nothing
                if (row.quantity <= 0) row.quantity = row.refdesList.size();
                rows.add(row);
            }
        }

        // 2) Components left over (no BOM line) → synthetic rows grouped by part name.
        // Many real archives ship without a boms/ file,
        // so this path also acts as a fallback BOM derived from the components and
        // their PRP records (manufacturer, MPN, etc.).
        int syntheticItem = 1;
        for (Map.Entry<String, List<Component>> entry : componentsByPart.entrySet()) {
            Row row = new Row();
            row.partNumber = entry.getKey();
            row.packageName = "";
            row.quantity = entry.getValue().size();
            row.orphan = true;
            row.item = syntheticItem++;
            // Lift descriptive metadata from the first component's PRP records.
            Component first = entry.getValue().isEmpty() ? null : entry.getValue().get(0);
            if (first != null) {
                row.manufacturer = lookupPrp(first, "Manufacturer", "Manufacturer_1");
                row.mpn = lookupPrp(first, "Manufacturer_Part_Number_1", "MPN", "Part_Number");
                row.description = buildDescriptionFromPrps(first);
            }
            for (Component c : entry.getValue()) {
                row.refdesList.add(toRefdesEntry(c, sideByRefdes));
            }
            rows.add(row);
        }

        return rows;
    }

    private static String lookupPrp(Component c, String... names) {
        if (c == null || c.getPropertyRecords() == null) return null;
        for (String want : names) {
            for (PropertyRecord pr : c.getPropertyRecords()) {
                if (pr.getName() != null && pr.getName().equalsIgnoreCase(want)) {
                    String v = pr.getValue();
                    if (v != null && !v.isEmpty()) return stripQuotes(v);
                }
            }
        }
        return null;
    }

    private static String buildDescriptionFromPrps(Component c) {
        if (c == null || c.getPropertyRecords() == null) return "";
        // Prefer a single 'Description' / 'Comment' / 'Value' property if present.
        for (String name : new String[]{"Description", "Comment", "Value"}) {
            String v = lookupPrp(c, name);
            if (v != null) return v;
        }
        // Otherwise compose a short summary from the first few non-URL PRPs.
        StringBuilder sb = new StringBuilder();
        for (PropertyRecord pr : c.getPropertyRecords()) {
            String n = pr.getName();
            String v = pr.getValue();
            if (n == null || v == null) continue;
            if (n.toLowerCase(Locale.ROOT).contains("url")) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(n).append("=").append(stripQuotes(v));
            if (sb.length() > 200) { sb.append("…"); break; }
        }
        return sb.toString();
    }

    private static String stripQuotes(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.length() >= 2
                && (s.charAt(0) == '\'' || s.charAt(0) == '"')
                && s.charAt(s.length() - 1) == s.charAt(0)) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    // ---- helpers ----

    private static RefdesEntry toRefdesEntry(Component c, Map<String, String> sideByRefdes) {
        RefdesEntry r = new RefdesEntry();
        r.refdes = c.getCompName();
        r.side = sideByRefdes.getOrDefault(r.refdes, "");
        // Component coords are already mm thanks to ComponentsParser's mmScale path.
        r.xMm = c.getX();
        r.yMm = c.getY();
        r.rotation = c.getRotation();
        r.mirror = c.getMirror() == MirrorType.MIRRORED ? "M" : "N";
        // Best-effort: component itself doesn't carry the resolved package string, only the
        // pkgRef. Leaving the package per-refdes blank is fine — the row-level packageName
        // already comes from the BOM line.
        r.packageName = null;
        return r;
    }

    private static String joinDescriptions(List<String> descriptions) {
        if (descriptions == null || descriptions.isEmpty()) return "";
        if (descriptions.size() == 1) return descriptions.get(0);
        StringBuilder sb = new StringBuilder();
        for (String d : descriptions) {
            if (d == null || d.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(d);
        }
        return sb.toString().trim();
    }

    /** Convenience: total number of placed components across all rows. */
    public static int totalRefdes(List<Row> rows) {
        int n = 0;
        for (Row r : rows) n += r.refdesList.size();
        return n;
    }

    /** Convenience: count rows that came from BOM lines (vs orphan). */
    public static int bomLineCount(List<Row> rows) {
        int n = 0;
        for (Row r : rows) if (!r.orphan) n++;
        return n;
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }
}
