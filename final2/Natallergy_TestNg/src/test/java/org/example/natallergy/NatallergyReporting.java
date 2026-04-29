package org.example.natallergy;

import org.openqa.selenium.WebDriver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * <p><b>If you only remember one thing:</b> every call to {@code takeScreenshot(...)} adds one step to the HTML report,
 * one row to the CSV, and saves <b>one full-page PNG file</b> on disk (in Chrome). The HTML page shows that PNG inside
 * a <b>short scrollable box</b> so the report stays readable — opening the PNG file still shows the entire page.</p>
 *
 * <p><b>Implementation order inside this file:</b> counters → PNG file → CSV row → HTML fragment → rewrite full HTML.</p>
 */
public final class NatallergyReporting {

    // -------------------------------------------------------------------------
    // Run-wide counters (TestNG runs one class instance per suite in typical setups)
    // -------------------------------------------------------------------------
    static int totalSteps = 0;
    static int passedSteps = 0;
    static int failedSteps = 0;
    static final List<String> htmlSteps = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Public API — use these from NatallergyFlow / NatallergyTest / NatallergyPageActions
    // -------------------------------------------------------------------------

    /** Records a passing step; optional page URL in HTML/CSV when you pass the 5-arg overload. */
    public static void takeScreenshot(WebDriver driver, String title) throws IOException {
        takeScreenshot(driver, title, true, "Step completed successfully", null);
    }

    public static void takeScreenshot(WebDriver driver, String title, boolean isPass, String details) throws IOException {
        takeScreenshot(driver, title, isPass, details, null);
    }

    /**
     * @param pageUrl optional; when non-blank, shown as “Page URL” in HTML and in the CSV {@code PageUrl} column
     *                (e.g. PDP URL from sitemap, or {@code driver.getCurrentUrl()} for cart/checkout).
     */
    public static void takeScreenshot(WebDriver driver, String title, boolean isPass, String details, String pageUrl)
            throws IOException {
        totalSteps++;
        if (isPass) {
            passedSteps++;
        } else {
            failedSteps++;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String statusPrefix = isPass ? "SUCCESS_" : "ERROR_";
        String fileName = statusPrefix + title + "_" + timestamp + ".png";

        File folder = new File(NatallergyConfig.SS_DIR);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File outputFile = new File(folder, fileName);

        // (1) Disk = full-page PNG
        NatallergyScreenshotCapture.saveCurrentPageAsPng(driver, outputFile);
        System.out.println("Full-page screenshot saved: " + outputFile.getAbsolutePath());

        // (2) CSV row
        appendCsvRow(timestamp, title, fileName, pageUrl == null ? "" : pageUrl);

        // (3) HTML step + full document rewrite
        htmlSteps.add(buildStepHtmlFragment(timestamp, title, fileName, isPass, details, pageUrl));
        File htmlFile = new File(NatallergyConfig.HTML_DIR + "\\TestReport.html");
        writeHtmlDocument(htmlFile, htmlSteps, totalSteps, passedSteps, failedSteps);
    }

    // -------------------------------------------------------------------------
    // CSV — one private method
    // -------------------------------------------------------------------------

    private static final String CSV_HEADER = "Timestamp,Title,LocalFile,PageUrl";

    private static void appendCsvRow(String timestamp, String title, String localFileName, String pageUrl) {
        File fileObj = new File(NatallergyConfig.CSV_PATH);
        if (!fileObj.getParentFile().exists()) {
            fileObj.getParentFile().mkdirs();
        }
        boolean fileExists = fileObj.exists();

        try (FileWriter fw = new FileWriter(NatallergyConfig.CSV_PATH, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            if (!fileExists) {
                out.println(CSV_HEADER);
            }
            out.println(timestamp + "," + escapeCsvField(title) + "," + escapeCsvField(localFileName) + "," + escapeCsvField(pageUrl));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String escapeCsvField(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\r") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // -------------------------------------------------------------------------
    // HTML — step fragment + full page template + CSS (preview frame ≠ file content)
    // -------------------------------------------------------------------------

    /**
     * Builds one step card. The {@code <img>} is inside {@code .screenshot-viewport}: fixed max height and vertical scroll,
     * so the report looks like a normal window; the linked PNG file is still the full-page capture.
     */
    private static String buildStepHtmlFragment(
            String timestamp,
            String title,
            String localFileName,
            boolean isPass,
            String details,
            String pageUrl) {

        String relativeImgPath = "../../../screenshots/" + NatallergyConfig.RUN_DATE + "/" + NatallergyConfig.RUN_TIME + "/" + localFileName;

        String stepStatusClass = isPass ? "pass" : "fail";
        String stepStatusIcon = isPass ? "✅" : "❌";
        String displayTitle = escapeHtml(title.replace("_", " ").toUpperCase());
        String timePart = escapeHtml(timestamp.contains("_") ? timestamp.split("_")[1].replace("-", ":") : timestamp);

        StringBuilder sb = new StringBuilder();
        sb.append("            <div class=\"test-step ").append(stepStatusClass).append("\">\n");
        sb.append("                <div class=\"step-header\">\n");
        sb.append("                    <span>").append(stepStatusIcon).append(" ").append(displayTitle).append("</span>\n");
        sb.append("                    <span class=\"step-time\">").append(timePart).append("</span>\n");
        sb.append("                </div>\n");
        sb.append("                <div class=\"step-details\">").append(escapeHtml(details)).append("</div>\n");
        sb.append("                <p class=\"preview-caption\"><strong>Preview:</strong> fixed-height window (scroll inside). ");
        sb.append("<strong>File on disk:</strong> full-page PNG — button below opens the complete image.</p>\n");
        if (pageUrl != null && !pageUrl.isBlank()) {
            sb.append("                <div class=\"step-page-url\"><strong>Page URL:</strong> ");
            sb.append("<a href=\"").append(escapeHtmlAttribute(pageUrl)).append("\" target=\"_blank\" rel=\"noopener\">");
            sb.append(escapeHtml(pageUrl)).append("</a></div>\n");
        }
        sb.append("                <div class=\"screenshot-viewport\" title=\"Scroll to see full-page capture\">\n");
        sb.append("                    <a href=\"").append(escapeHtmlAttribute(relativeImgPath)).append("\" target=\"_blank\">\n");
        sb.append("                        <img class=\"screenshot\" src=\"").append(escapeHtmlAttribute(relativeImgPath)).append("\" alt=\"");
        sb.append(escapeHtmlAttribute(title)).append("\">\n");
        sb.append("                    </a>\n");
        sb.append("                </div>\n");
        sb.append("                <div class=\"step-actions\">\n");
        sb.append("                    <a class=\"btn\" href=\"").append(escapeHtmlAttribute(relativeImgPath)).append("\" target=\"_blank\">Open full-page PNG</a>\n");
        sb.append("                </div>\n");
        sb.append("            </div>\n");

        return sb.toString();
    }

    private static void writeHtmlDocument(File htmlFile, List<String> steps, int totalSteps, int passedSteps, int failedSteps) {
        File parent = htmlFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileWriter fw = new FileWriter(htmlFile, false);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            double passRate = totalSteps > 0 ? ((double) passedSteps / totalSteps) * 100 : 0;
            String overallStatus = failedSteps > 0 ? "FAILED" : "PASSED";
            String statusBadgeClass = failedSteps > 0 ? "status-fail" : "status-pass";
            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("    <meta charset=\"UTF-8\">");
            out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.println("    <title>National Allergy — Test Report</title>");
            out.println("    <style>");
            out.print(reportStylesheet());
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("    <div class=\"container\">");
            out.println("        <div class=\"header\">");
            out.println("            <h1>National Allergy — Automation</h1>");
            out.println("            <p class=\"header-sub\">Screenshots are <strong>full page</strong> on disk; below each step you see a <strong>scrollable preview</strong> (normal window height).</p>");
            out.println("            <div class=\"timestamp\">Generated: " + NatallergyConfig.RUN_DATE + " " + NatallergyConfig.RUN_TIME.replace("-", ":") + "</div>");
            out.println("        </div>");
            out.println("        <div class=\"summary\">");
            out.println("            <div class=\"summary-card\"><h3>Overall</h3><div class=\"status-badge " + statusBadgeClass + "\">" + overallStatus + "</div></div>");
            out.println("            <div class=\"summary-card\"><h3>Steps</h3><div class=\"number\">" + totalSteps + "</div></div>");
            out.println("            <div class=\"summary-card\"><h3>Passed</h3><div class=\"number ok\">" + passedSteps + "</div></div>");
            out.println("            <div class=\"summary-card\"><h3>Failed</h3><div class=\"number bad\">" + failedSteps + "</div></div>");
            out.println("            <div class=\"summary-card\"><h3>Pass rate</h3><div class=\"number\">" + String.format("%.1f", passRate) + "%</div>");
            out.println("                <div class=\"progress-bar\"><div class=\"progress-fill\" style=\"width:" + passRate + "%\"></div></div></div>");
            out.println("        </div>");
            out.println("        <p class=\"duration\"><strong>Duration:</strong> " + NatallergyConfig.START_TIME + " → " + currentTime + "</p>");
            out.println("        <div class=\"test-results\"><h2>Steps</h2>");
            for (String step : steps) {
                out.print(step);
            }
            out.println("        </div>");
            out.println("        <footer>Natallergy TestNG automation</footer>");
            out.println("    </div></body></html>");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** One place for all report CSS — preview frame limits height; image inside is full resolution. */
    private static String reportStylesheet() {
        return """
            body{font-family:Segoe UI,Tahoma,sans-serif;margin:0;padding:20px;background:linear-gradient(135deg,#f5f7fa,#c3cfe2);}
            .container{max-width:1200px;margin:0 auto;background:#fff;border-radius:12px;box-shadow:0 8px 24px rgba(0,0,0,.08);padding:32px;}
            .header{text-align:center;background:linear-gradient(135deg,#667eea,#764ba2);color:#fff;padding:28px;border-radius:12px;margin-bottom:28px;}
            .header h1{margin:0;font-size:2em;font-weight:400;}
            .header-sub{font-size:1rem;margin:12px 0 0;opacity:.95;line-height:1.5;}
            .timestamp{font-size:.9rem;opacity:.9;margin-top:8px;}
            .summary{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:16px;margin-bottom:24px;}
            .summary-card{background:#f8fafc;padding:16px;border-radius:10px;text-align:center;border-left:4px solid #667eea;}
            .summary-card h3{margin:0 0 8px;font-size:.95rem;color:#475569;}
            .summary-card .number{font-size:1.75rem;font-weight:700;color:#1e293b;}
            .summary-card .number.ok{color:#16a34a;}
            .summary-card .number.bad{color:#dc2626;}
            .progress-bar{height:10px;background:#e2e8f0;border-radius:6px;overflow:hidden;margin-top:8px;}
            .progress-fill{height:100%;background:linear-gradient(90deg,#22c55e,#14b8a6);}
            .status-badge{display:inline-block;padding:6px 16px;border-radius:20px;color:#fff;font-weight:600;font-size:.9rem;}
            .status-pass{background:#16a34a;}
            .status-fail{background:#dc2626;}
            .duration{text-align:center;color:#64748b;margin:16px 0;}
            .test-results h2{margin:0 0 16px;color:#334155;}
            .test-step{margin:16px 0;padding:18px;border-radius:10px;border-left:4px solid;}
            .test-step.pass{background:linear-gradient(135deg,#ecfdf5,#d1fae5);border-left-color:#22c55e;}
            .test-step.fail{background:linear-gradient(135deg,#fef2f2,#fecaca);border-left-color:#ef4444;}
            .step-header{font-weight:600;margin-bottom:8px;font-size:1.05rem;color:#0f172a;}
            .step-time{float:right;font-size:.8rem;color:#64748b;background:#f1f5f9;padding:4px 8px;border-radius:6px;}
            .step-details{font-size:.9rem;color:#475569;line-height:1.5;margin-bottom:6px;}
            .preview-caption{font-size:.85rem;color:#64748b;margin:8px 0;line-height:1.45;}
            .step-page-url{font-size:.85rem;margin:6px 0;word-break:break-all;}
            .step-page-url a{color:#4f46e5;}
            .screenshot-viewport{max-width:100%;width:min(100%,1200px);max-height:520px;overflow:auto;border:1px solid #cbd5e1;border-radius:8px;background:#f8fafc;margin-top:10px;box-shadow:inset 0 1px 3px rgba(0,0,0,.06);}
            .screenshot-viewport a{display:block;line-height:0;}
            .screenshot-viewport .screenshot{max-width:100%;width:auto;height:auto;display:block;margin:0;border:0;border-radius:0;vertical-align:top;}
            .step-actions{margin-top:10px;}
            .btn{display:inline-block;padding:8px 14px;background:#0f766e;color:#fff;text-decoration:none;border-radius:8px;font-size:.85rem;font-weight:600;}
            .btn:hover{opacity:.92;}
            footer{text-align:center;margin-top:32px;padding-top:16px;border-top:1px solid #e2e8f0;color:#94a3b8;font-size:.85rem;}
            """;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeHtmlAttribute(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    private NatallergyReporting() {
    }
}
