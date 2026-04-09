package nutridyn;

import org.openqa.selenium.*;
import org.openqa.selenium.chromium.HasCdp;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NutridynReporting {

    static String RUN_DATE;
    static String RUN_TIME;
    static int totalSteps;
    static int passedSteps;
    static int failedSteps;
    static String START_TIME;
    static List<String> htmlSteps = new ArrayList<>();
    static String SS_DIR;
    static String HTML_DIR;
    static String CSV_PATH;

    private NutridynReporting() {
    }

    public static void beginNewRun() {
        Date now = new Date();
        RUN_DATE = new SimpleDateFormat("yyyy-MM-dd").format(now);
        RUN_TIME = new SimpleDateFormat("HH-mm-ss").format(now);
        START_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(now);
        totalSteps = 0;
        passedSteps = 0;
        failedSteps = 0;
        htmlSteps = new ArrayList<>();
        SS_DIR = NutridynConfig.OUTPUT_ROOT + "\\screenshots\\" + RUN_DATE + "\\" + RUN_TIME;
        HTML_DIR = NutridynConfig.OUTPUT_ROOT + "\\html\\" + RUN_DATE + "\\" + RUN_TIME;
        CSV_PATH = NutridynConfig.OUTPUT_ROOT + "\\Nutridyn.csv";
    }
    public static void takeScreenshot(WebDriver driver, String title) throws IOException, InterruptedException {
        takeScreenshot(driver, title, true, "Step completed successfully", null);
    }

    public static void takeScreenshot(WebDriver driver, String title, boolean isPass, String details) throws IOException, InterruptedException {
        takeScreenshot(driver, title, isPass, details, null);
    }

    /**
     * @param pageUrl optional storefront URL for this step (category, product, etc.) — shown as a clickable link in the HTML report
     */
    public static void takeScreenshot(WebDriver driver, String title, boolean isPass, String details, String pageUrl) throws IOException, InterruptedException {
        totalSteps++;
        if (isPass) passedSteps++;
        else failedSteps++;

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String statusPrefix = isPass ? "SUCCESS_" : "ERROR_";
        String fileName = statusPrefix + title + "_" + timestamp + ".png";

        File folder = new File(SS_DIR);
        if (!folder.exists()) folder.mkdirs();

        File outputFile = new File(folder, fileName);
        saveFullPageScreenshot(driver, outputFile);

        System.out.println("Full-page screenshot saved: " + outputFile.getAbsolutePath());

        String headline = formatTitleWithStatusIcon(title, isPass);
        writeCsv(timestamp, headline, outputFile.getName());
        writeHtmlReport(timestamp, title, headline, outputFile.getName(), isPass, details, pageUrl);
    }

    /** Report/CSV line: checkmark (or X) + upper-case words, e.g. {@code \u2705 COOKIE ACCEPTED}. */
    public static String formatTitleWithStatusIcon(String titleSlug, boolean isPass) {
        String words = titleSlug.replace("_", " ").trim().toUpperCase();
        return (isPass ? "\u2705 " : "\u274C ") + words;
    }

    private static double cdpNumber(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }

    /**
     * Chrome: true full-page PNG via layout size + device metrics (viewport-only CDP is unreliable on some builds).
     * Falls back to captureBeyondViewport, then WebDriver screenshot.
     */
    private static void saveFullPageScreenshot(WebDriver driver, File outputFile) throws IOException, InterruptedException {
        if (!(driver instanceof HasCdp hasCdp)) {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(src.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        Map<String, Object> empty = new HashMap<>();
        try {
            Map<String, Object> metrics = hasCdp.executeCdpCommand("Page.getLayoutMetrics", empty);
            @SuppressWarnings("unchecked")
            Map<String, Object> box = (Map<String, Object>) metrics.get("cssContentSize");
            if (box == null || box.isEmpty()) {
                box = (Map<String, Object>) metrics.get("contentSize");
            }

            int width = 1280;
            int height = 2000;
            if (box != null && box.get("width") != null && box.get("height") != null) {
                width = (int) Math.ceil(cdpNumber(box.get("width")));
                height = (int) Math.ceil(cdpNumber(box.get("height")));
            }
            width = Math.clamp(width, 400, 4096);
            height = Math.clamp(height, 400, 25000);

            Map<String, Object> override = new HashMap<>();
            override.put("width", width);
            override.put("height", height);
            override.put("deviceScaleFactor", 1);
            override.put("mobile", false);

            hasCdp.executeCdpCommand("Emulation.setDeviceMetricsOverride", override);
            Thread.sleep(400);
            try {
                Map<String, Object> cap = new HashMap<>();
                cap.put("format", "png");
                cap.put("captureBeyondViewport", true);
                cap.put("fromSurface", true);
                Map<String, Object> result = hasCdp.executeCdpCommand("Page.captureScreenshot", cap);
                String data = (String) result.get("data");
                Files.write(outputFile.toPath(), Base64.getDecoder().decode(data));
            } finally {
                hasCdp.executeCdpCommand("Emulation.clearDeviceMetricsOverride", empty);
            }
        } catch (Exception e) {
            System.out.println("Full-page capture (layout metrics) failed: " + e.getMessage() + " - using CDP viewport capture.");
            try {
                Map<String, Object> cap = new HashMap<>();
                cap.put("format", "png");
                cap.put("captureBeyondViewport", true);
                cap.put("fromSurface", true);
                Map<String, Object> result = hasCdp.executeCdpCommand("Page.captureScreenshot", cap);
                String data = (String) result.get("data");
                Files.write(outputFile.toPath(), Base64.getDecoder().decode(data));
            } catch (Exception e2) {
                File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                Files.copy(src.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void writeCsv(String timestamp, String title, String localFileName) {
        File fileObj = new File(CSV_PATH);
        if (!fileObj.getParentFile().exists()) {
            fileObj.getParentFile().mkdirs();
        }
        boolean fileExists = fileObj.exists();

        try (Writer fw = new OutputStreamWriter(new FileOutputStream(CSV_PATH, true), StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            if (!fileExists) {
                out.println("Timestamp,Title,LocalFile");
            }
            out.println(timestamp + "," + title + "," + localFileName);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String escAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;");
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean isHttpOrHttps(String u) {
        return u != null && !u.isBlank() && (u.startsWith("https://") || u.startsWith("http://"));
    }

    private static void writeHtmlReport(String timestamp, String titleSlug, String headline, String localFileName, boolean isPass, String details, String pageUrl) {
        File htmlFolder = new File(HTML_DIR);
        if (!htmlFolder.exists()) htmlFolder.mkdirs();

        String htmlFile = HTML_DIR + "\\TestReport.html";
        File imageFile = new File(SS_DIR, localFileName);
        String fileUri = imageFile.getAbsoluteFile().toURI().toString();

        String stepStatusClass = isPass ? "pass" : "fail";

        StringBuilder stepHtml = new StringBuilder();
        stepHtml.append("            <div class=\"test-step ").append(stepStatusClass).append("\">\n");
        stepHtml.append("                <div class=\"step-header\">\n");
        stepHtml.append("                    <span>").append(escHtml(headline)).append("</span>\n");
        stepHtml.append("                    <span class=\"step-time\">").append(timestamp.split("_")[1].replace("-", ":")).append("</span>\n");
        stepHtml.append("                </div>\n");
        stepHtml.append("                <div class=\"step-details\">").append(escHtml(details)).append("</div>\n");
        if (isHttpOrHttps(pageUrl)) {
            String u = pageUrl.trim();
            stepHtml.append("                <div class=\"step-page-url\"><strong>Opened URL:</strong> ");
            stepHtml.append("<a href=\"").append(escAttr(u)).append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                    .append(escHtml(u)).append("</a></div>\n");
        }
        stepHtml.append("                <div class=\"screenshot-wrap\">\n");
        stepHtml.append("                    <p class=\"screenshot-note\">Thumbnail preview (full-page image is still saved on disk).</p>\n");
        stepHtml.append("                    <a class=\"screenshot-link\" href=\"").append(fileUri).append("\" target=\"_blank\" rel=\"noopener\">\n");
        stepHtml.append("                        <img class=\"screenshot\" src=\"").append(fileUri).append("\" alt=\"")
                .append(escAttr(titleSlug)).append(" screenshot\" loading=\"lazy\">\n");
        stepHtml.append("                    </a>\n");
        stepHtml.append("                    <div class=\"screenshot-actions\">\n");
        stepHtml.append("                        <a class=\"btn\" href=\"").append(fileUri).append("\" target=\"_blank\" rel=\"noopener\">View Local</a>\n");
        stepHtml.append("                        <a class=\"btn btn-secondary\" href=\"").append(fileUri).append("\" target=\"_blank\" rel=\"noopener\">View full size</a>\n");
        stepHtml.append("                    </div>\n");
        stepHtml.append("                </div>\n");
        stepHtml.append("            </div>");

        htmlSteps.add(stepHtml.toString());

        try (Writer fw = new OutputStreamWriter(new FileOutputStream(htmlFile), StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            double passRate = totalSteps > 0 ? ((double) passedSteps / totalSteps) * 100 : 0;
            String overallStatus = failedSteps > 0 ? "FAILED" : "PASSED";
            String statusBadgeClass = failedSteps > 0 ? "status-fail" : "status-pass";

            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("    <meta charset=\"UTF-8\">");
            out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            out.println("    <title>NutriDyn Automation - Test Report</title>");
            out.println("    <style>");
            out.println("        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 20px; background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%); }");
            out.println("        .container { max-width: min(1920px, 100%); margin: 0 auto; background: white; border-radius: 15px; box-shadow: 0 10px 30px rgba(0,0,0,0.1); padding: 40px; }");
            out.println("        .header { text-align: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px; border-radius: 15px; margin-bottom: 40px; }");
            out.println("        .header h1 { margin: 0; font-size: 2.5em; font-weight: 300; }");
            out.println("        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 25px; margin-bottom: 40px; }");
            out.println("        .summary-card { background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); padding: 25px; border-radius: 15px; text-align: center; border-left: 6px solid #667eea; transition: transform 0.3s; }");
            out.println("        .summary-card:hover { transform: translateY(-5px); }");
            out.println("        .summary-card h3 { margin: 0 0 15px 0; color: #333; font-size: 1.1em; }");
            out.println("        .summary-card .number { font-size: 2.5em; font-weight: bold; color: #333; margin-bottom: 10px; }");
            out.println("        .progress-bar { width: 100%; height: 25px; background-color: #e9ecef; border-radius: 12px; overflow: hidden; margin: 15px 0; }");
            out.println("        .progress-fill { height: 100%; background: linear-gradient(90deg, #28a745, #20c997); transition: width 1s ease; border-radius: 12px; }");
            out.println("        .test-results { margin: 40px 0; display: flex; flex-direction: column; gap: 15px; align-items: stretch; width: 100%; }");
            out.println("        .test-step { margin: 15px 0; padding: 20px; border-radius: 12px; border-left: 6px solid; transition: all 0.3s; width: 100%; box-sizing: border-box; min-width: 0; }");
            out.println("        .test-step:hover { transform: translateX(5px); }");
            out.println("        .test-step.pass { background: linear-gradient(135deg, #d4edda 0%, #c3e6cb 100%); border-left-color: #28a745; }");
            out.println("        .test-step.fail { background: linear-gradient(135deg, #f8d7da 0%, #f5c6cb 100%); border-left-color: #dc3545; }");
            out.println("        .step-header { font-weight: bold; margin-bottom: 12px; font-size: 1.1em; }");
            out.println("        .step-details { font-size: 0.95em; color: #666; line-height: 1.5; }");
            out.println("        .step-page-url { font-size: 0.92em; margin: 10px 0 6px 0; line-height: 1.5; word-break: break-all; }");
            out.println("        .step-page-url a { color: #0b5ed7; font-weight: 600; }");
            out.println("        .step-time { font-size: 0.85em; color: #888; float: right; background: rgba(0,0,0,0.05); padding: 4px 8px; border-radius: 5px; }");
            out.println("        .screenshot-wrap { width: 100%; margin: 20px 0; padding: 14px 12px; background: #f1f3f5; border-radius: 10px; border: 1px solid #dee2e6; box-sizing: border-box; min-width: 0; text-align: center; }");
            out.println("        .screenshot-actions { margin-top: 14px; display: flex; flex-wrap: wrap; gap: 10px; align-items: center; justify-content: center; }");
            out.println("        .screenshot-note { margin: 0 0 12px 0; font-size: 0.9em; color: #495057; font-weight: 600; text-align: center; }");
            out.println("        .screenshot-link { display: inline-block; line-height: 0; max-width: 100%; }");
            out.println("        .screenshot { max-width: min(400px, 100%); max-height: 240px; width: auto; height: auto; object-fit: contain; display: block; margin: 0 auto; border-radius: 6px; border: 1px solid #ced4da; box-shadow: 0 2px 8px rgba(0,0,0,0.08); cursor: pointer; background: #fff; padding: 4px; image-rendering: auto; }");
            out.println("        .timestamp { text-align: center; color: #666; margin: 25px 0; font-size: 1.1em; }");
            out.println("        .status-badge { display: inline-block; padding: 8px 20px; border-radius: 25px; color: white; font-weight: bold; }");
            out.println("        .status-pass { background: linear-gradient(135deg, #28a745 0%, #20c997 100%); }");
            out.println("        .status-fail { background: linear-gradient(135deg, #dc3545 0%, #c82333 100%); }");
            out.println("        .btn { display: inline-block; padding: 8px 18px; background: linear-gradient(135deg, #28a745 0%, #20c997 100%); color: white; text-decoration: none; border-radius: 20px; font-weight: bold; font-size: 0.9em; transition: opacity 0.3s; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }");
            out.println("        .btn:hover { opacity: 0.9; }");
            out.println("        .btn-secondary { background: linear-gradient(135deg, #17a2b8 0%, #117a8b 100%); }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("    <div class=\"container\">");
            out.println("        <div class=\"header\">");
            out.println("            <h1>NutriDyn Automation</h1>");
            out.println("            <p style=\"font-size: 1.2em; margin: 10px 0;\">Test Report with Detailed Steps</p>");
            out.println("            <div class=\"timestamp\">Generated on: " + RUN_DATE + " at " + RUN_TIME.replace("-", ":") + "</div>");
            out.println("        </div>");
            out.println("        <div class=\"summary\">");
            out.println("            <div class=\"summary-card\">");
            out.println("                <h3>Overall Status</h3>");
            out.println("                <div class=\"status-badge " + statusBadgeClass + "\">" + overallStatus + "</div>");
            out.println("            </div>");
            out.println("            <div class=\"summary-card\">");
            out.println("                <h3>Total Steps</h3>");
            out.println("                <div class=\"number\">" + totalSteps + "</div>");
            out.println("            </div>");
            out.println("            <div class=\"summary-card\">");
            out.println("                <h3>Passed</h3>");
            out.println("                <div class=\"number\" style=\"color: #28a745;\">" + passedSteps + "</div>");
            out.println("            </div>");
            out.println("            <div class=\"summary-card\">");
            out.println("                <h3>Failed</h3>");
            out.println("                <div class=\"number\" style=\"color: #dc3545;\">" + failedSteps + "</div>");
            out.println("            </div>");
            out.println("            <div class=\"summary-card\">");
            out.println("                <h3>Pass Rate</h3>");
            out.println("                <div class=\"number\">" + String.format("%.1f", passRate) + "%</div>");
            out.println("                <div class=\"progress-bar\">");
            out.println("                    <div class=\"progress-fill\" style=\"width: " + passRate + "%\"></div>");
            out.println("                </div>");
            out.println("            </div>");
            out.println("        </div>");
            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            out.println("        <div style=\"text-align: center; margin: 20px 0;\">");
            out.println("            <p><strong>Test Duration:</strong> " + START_TIME + " to " + currentTime + "</p>");
            out.println("        </div>");
            out.println("        <div class=\"test-results\">");
            out.println("            <h2>Detailed Test Results</h2>");
            out.println("            <p style=\"color: #666; margin-bottom: 30px;\">Each step shows a <strong>small thumbnail</strong> of the saved screenshot for quick scanning. Use <strong>View Local</strong> or <strong>View full size</strong> to open the full-resolution PNG (still full-page capture on disk).</p>");
            for (String step : htmlSteps) {
                out.println(step);
            }
            out.println("        </div>");
            out.println("        <div style=\"margin-top: 50px; padding-top: 20px; border-top: 1px solid #dee2e6; text-align: center; color: #6c757d;\">");
            out.println("            <p>Generated by NutriDyn Automation Framework</p>");
            out.println("        </div>");
            out.println("    </div>");
            out.println("</body>");
            out.println("</html>");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
