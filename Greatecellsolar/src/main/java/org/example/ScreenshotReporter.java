package org.example;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every test “step” that should appear in the report calls {@link #takeFullPageScreenshot}.
 *
 * <p><b>Outputs (under {@link GreatCellConfig})</b>
 * <ul>
 *   <li>PNG files in {@link GreatCellConfig#SS_DIR} — prefix {@code SUCCESS_} or {@code ERROR_} from {@code isPass}</li>
 *   <li>Append row to {@link GreatCellConfig#CSV_PATH}</li>
 *   <li>Rewrite {@code TestReport.html} under {@link GreatCellConfig#HTML_DIR} with all steps so far</li>
 * </ul>
 *
 * <p>Chrome: tries CDP full-page capture; on failure falls back to viewport-only and notes that in {@code details}.
 */
public final class ScreenshotReporter {

    private ScreenshotReporter() {}

    /** Running totals for the HTML summary header (pass rate, counts). */
    static int totalSteps = 0;
    static int passedSteps = 0;
    static int failedSteps = 0;
    /** HTML fragment per step; rebuilt into full report on each new screenshot. */
    static final List<String> htmlSteps = new ArrayList<>();

    /** Same as {@link #takeFullPageScreenshot(WebDriver, String, boolean, String)} with pass=true and a generic detail. */
    public static void takeFullPageScreenshot(WebDriver driver, String title) throws IOException {
        takeFullPageScreenshot(driver, title, true, "Full page capture");
    }

    /**
     * Captures screenshot, logs CSV, appends step to HTML report.
     *
     * @param title short id used in filename and report (use snake_case, e.g. {@code category_from_sitemap})
     * @param isPass {@code false} → ERROR_ prefix and red styling in HTML
     * @param details human-readable explanation shown under the step title
     */
    public static void takeFullPageScreenshot(WebDriver driver, String title, boolean isPass, String details)
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

        File folder = new File(GreatCellConfig.SS_DIR);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File outputFile = new File(folder, fileName);
        byte[] pngBytes;
        String reportDetails = details;

        if (driver instanceof ChromeDriver) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("format", "png");
                params.put("captureBeyondViewport", true);
                params.put("fromSurface", true);
                Map<String, Object> result =
                        ((ChromeDriver) driver).executeCdpCommand("Page.captureScreenshot", params);
                String data = (String) result.get("data");
                pngBytes = Base64.getDecoder().decode(data);
            } catch (WebDriverException e) {
                System.out.println("CDP full-page capture failed (upgrade selenium-devtools to match Chrome): "
                        + e.getMessage());
                File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                pngBytes = Files.readAllBytes(src.toPath());
                reportDetails = details + " | Viewport only (CDP unavailable)";
            }
        } else {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            pngBytes = Files.readAllBytes(src.toPath());
            reportDetails = details + " | Viewport only (not ChromeDriver)";
        }

        Files.write(outputFile.toPath(), pngBytes);
        System.out.println("Screenshot saved: " + outputFile.getAbsolutePath());

        writeCsv(timestamp, title, outputFile.getName());
        writeHtmlReport(timestamp, title, outputFile.getName(), isPass, reportDetails);
    }

    private static void writeCsv(String timestamp, String title, String localFileName) {
        File fileObj = new File(GreatCellConfig.CSV_PATH);
        if (!fileObj.getParentFile().exists()) {
            fileObj.getParentFile().mkdirs();
        }
        boolean fileExists = fileObj.exists();

        try (FileWriter fw = new FileWriter(GreatCellConfig.CSV_PATH, true);
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

    private static void writeHtmlReport(
            String timestamp, String title, String localFileName, boolean isPass, String details) {
        File htmlFolder = new File(GreatCellConfig.HTML_DIR);
        if (!htmlFolder.exists()) {
            htmlFolder.mkdirs();
        }

        String htmlFile = GreatCellConfig.HTML_DIR + "\\TestReport.html";
        String relativeImgPath =
                "../../../screenshots/" + GreatCellConfig.RUN_DATE + "/" + GreatCellConfig.RUN_TIME + "/"
                        + localFileName;

        String stepStatusClass = isPass ? "pass" : "fail";
        String stepStatusIcon = isPass ? "✅" : "❌";

        StringBuilder stepHtml = new StringBuilder();
        stepHtml.append("            <div class=\"test-step ").append(stepStatusClass).append("\">\n");
        stepHtml.append("                <div class=\"step-header\">\n");
        stepHtml.append("                    <span>")
                .append(stepStatusIcon)
                .append(" ")
                .append(title.replace("_", " ").toUpperCase())
                .append("</span>\n");
        stepHtml.append("                    <span class=\"step-time\">")
                .append(timestamp.split("_")[1].replace("-", ":"))
                .append("</span>\n");
        stepHtml.append("                </div>\n");
        stepHtml.append("                <div class=\"step-details\">").append(details).append("</div>\n");
        stepHtml.append("                <div style=\"margin-top: 15px;\">\n");
        stepHtml.append("                    <a href=\"").append(relativeImgPath).append("\" target=\"_blank\">\n");
        stepHtml.append("                        <img class=\"screenshot\" src=\"")
                .append(relativeImgPath)
                .append("\" alt=\"")
                .append(title)
                .append("\">\n");
        stepHtml.append("                    </a>\n");
        stepHtml.append("                </div>\n");
        stepHtml.append("                <div style=\"margin-top: 10px;\">\n");
        stepHtml.append("                    <a class=\"btn\" href=\"")
                .append(relativeImgPath)
                .append("\" target=\"_blank\">Open screenshot</a>\n");
        stepHtml.append("                </div>\n");
        stepHtml.append("            </div>");

        htmlSteps.add(stepHtml.toString());

        try (FileWriter fw = new FileWriter(htmlFile, false);
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
            out.println("    <title>Great Cell Solar Materials - Test Report</title>");
            out.println("    <style>");
            out.println(
                    "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 20px; background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%); }");
            out.println(
                    "        .container { max-width: 1400px; margin: 0 auto; background: white; border-radius: 15px; box-shadow: 0 10px 30px rgba(0,0,0,0.1); padding: 40px; }");
            out.println(
                    "        .header { text-align: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px; border-radius: 15px; margin-bottom: 40px; }");
            out.println("        .header h1 { margin: 0; font-size: 2.5em; font-weight: 300; }");
            out.println(
                    "        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 25px; margin-bottom: 40px; }");
            out.println(
                    "        .summary-card { background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); padding: 25px; border-radius: 15px; text-align: center; border-left: 6px solid #667eea; transition: transform 0.3s; }");
            out.println("        .summary-card:hover { transform: translateY(-5px); }");
            out.println("        .summary-card h3 { margin: 0 0 15px 0; color: #333; font-size: 1.1em; }");
            out.println(
                    "        .summary-card .number { font-size: 2.5em; font-weight: bold; color: #333; margin-bottom: 10px; }");
            out.println(
                    "        .progress-bar { width: 100%; height: 25px; background-color: #e9ecef; border-radius: 12px; overflow: hidden; margin: 15px 0; }");
            out.println(
                    "        .progress-fill { height: 100%; background: linear-gradient(90deg, #28a745, #20c997); transition: width 1s ease; border-radius: 12px; }");
            out.println(
                    "        .test-results { margin: 40px 0; display: flex; flex-direction: column; gap: 15px; }");
            out.println(
                    "        .test-step { margin: 15px 0; padding: 20px; border-radius: 12px; border-left: 6px solid; transition: all 0.3s; }");
            out.println("        .test-step:hover { transform: translateX(5px); }");
            out.println(
                    "        .test-step.pass { background: linear-gradient(135deg, #d4edda 0%, #c3e6cb 100%); border-left-color: #28a745; }");
            out.println(
                    "        .test-step.fail { background: linear-gradient(135deg, #f8d7da 0%, #f5c6cb 100%); border-left-color: #dc3545; }");
            out.println("        .step-header { font-weight: bold; margin-bottom: 12px; font-size: 1.1em; }");
            out.println("        .step-details { font-size: 0.95em; color: #666; line-height: 1.5; }");
            out.println(
                    "        .step-time { font-size: 0.85em; color: #888; float: right; background: rgba(0,0,0,0.05); padding: 4px 8px; border-radius: 5px; }");
            out.println(
                    "        .screenshot { max-width: 300px; border-radius: 8px; margin: 15px 0; border: 2px solid #ddd; transition: transform 0.3s; cursor: pointer; }");
            out.println("        .screenshot:hover { transform: scale(1.05); }");
            out.println(
                    "        .timestamp { text-align: center; color: #666; margin: 25px 0; font-size: 1.1em; }");
            out.println(
                    "        .status-badge { display: inline-block; padding: 8px 20px; border-radius: 25px; color: white; font-weight: bold; }");
            out.println(
                    "        .status-pass { background: linear-gradient(135deg, #28a745 0%, #20c997 100%); }");
            out.println(
                    "        .status-fail { background: linear-gradient(135deg, #dc3545 0%, #c82333 100%); }");
            out.println(
                    "        .btn { display: inline-block; padding: 8px 15px; background: linear-gradient(135deg, #28a745 0%, #20c997 100%); color: white; text-decoration: none; border-radius: 20px; font-weight: bold; font-size: 0.9em; margin-right: 10px; transition: opacity 0.3s; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }");
            out.println("        .btn:hover { opacity: 0.9; }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("    <div class=\"container\">");
            out.println("        <div class=\"header\">");
            out.println("            <h1>Great Cell Solar Materials</h1>");
            out.println("            <p style=\"font-size: 1.2em; margin: 10px 0;\">Test Report with Detailed Steps</p>");
            out.println("            <div class=\"timestamp\">Generated on: " + GreatCellConfig.RUN_DATE + " at "
                    + GreatCellConfig.RUN_TIME.replace("-", ":") + "</div>");
            out.println("        </div>");
            out.println("        <div class=\"summary\">");
            out.println("            <div class=\"summary-card\">");
            out.println("                <h3>Overall Status</h3>");
            out.println("                <div class=\"status-badge " + statusBadgeClass + "\">" + overallStatus
                    + "</div>");
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
            out.println("            <p><strong>Test Duration:</strong> " + GreatCellConfig.START_TIME + " to "
                    + currentTime + "</p>");
            out.println("        </div>");
            out.println("        <div class=\"test-results\">");
            out.println("            <h2>Detailed Test Results</h2>");
            out.println(
                    "            <p style=\"color: #666; margin-bottom: 30px;\">Step-by-step execution details with screenshots</p>");
            for (String step : htmlSteps) {
                out.println(step);
            }
            out.println("        </div>");
            out.println(
                    "        <div style=\"margin-top: 50px; padding-top: 20px; border-top: 1px solid #dee2e6; text-align: center; color: #6c757d;\">");
            out.println("            <p>Generated by Great Cell Solar Materials automation</p>");
            out.println("        </div>");
            out.println("    </div>");
            out.println("</body>");
            out.println("</html>");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
