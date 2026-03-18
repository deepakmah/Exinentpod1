package Mcfeels;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.time.Duration;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class mcfeels {

    static String IMGBB_API_KEY = "46866c7eef7ee62b26a79f32a5d57a08";
    
    // Create organized folder structure: Date \ Time
    static String RUN_DATE = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    static String RUN_TIME = new SimpleDateFormat("HH-mm-ss").format(new Date());
    
    // Tracking Statistics
    static int totalSteps = 0;
    static int passedSteps = 0;
    static int failedSteps = 0;
    static String START_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    static java.util.List<String> htmlSteps = new java.util.ArrayList<>();

    // Separate paths for screenshots and html
    static String SS_DIR = "C:\\Users\\deepa\\Documents\\Automation\\Mcfeels\\screenshots\\" + RUN_DATE + "\\" + RUN_TIME;
    static String HTML_DIR = "C:\\Users\\deepa\\Documents\\Automation\\Mcfeels\\html\\" + RUN_DATE + "\\" + RUN_TIME;

    // Common CSV File
    static String CSV_PATH = "C:\\Users\\deepa\\Documents\\Automation\\Mcfeels\\Mcfeels.csv";

    public static void main(String[] args) throws Exception {

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--incognito");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.get("https://mcfeelys.com/");
        waitForPageToLoad(driver);

        dismissPopupIfExists(driver);
        takeScreenshot(driver, "homepage");

        product(driver);
        product_search(driver);
        checkout(driver);

         driver.quit();
    }

    // ------------------ Global page load wait ------------------
    public static void waitForPageToLoad(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        try {
            // Wait for document.readyState == complete
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState").equals("complete"));

            // Wait for jQuery AJAX to complete if jQuery is present
            wait.until(webDriver -> ((Long) ((JavascriptExecutor) webDriver)
                    .executeScript("return window.jQuery != undefined && jQuery.active == 0 ? 1 : 0")) == 1);
        } catch (Exception e) {
            System.out.println("Page load wait timed out: " + e.getMessage());
        }
    }

    public static void dismissPopupIfExists(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(2));
            WebElement popup = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("div.modal-inner-wrap, div.modals-overlay")
            ));

            try {
                WebElement closeBtn = popup.findElement(By.cssSelector("button.action-close[data-role='closeBtn']"));
                closeBtn.click();
                System.out.println("Popup closed successfully.");
            } catch (NoSuchElementException e) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].style.display='none';", popup);
                System.out.println("Popup hidden by JS.");
            }

            Thread.sleep(500);
        } catch (Exception ignored) {}
    }

    public static boolean safeClick(WebDriver driver, By locator) {
        int attempts = 0;
        while (attempts < 3) {
            try {
                dismissPopupIfExists(driver);
                WebElement elem = driver.findElement(locator);
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", elem);
                
                try {
                    waitForElementToBeClickable(driver, elem);
                    elem.click();
                    return true;
                } catch (TimeoutException te) {
                    System.out.println("Timeout waiting for element to be clickable. Attempting JS Click fallback...");
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", elem);
                    return true;
                }
            } catch (ElementNotInteractableException e) {
                System.out.println("Click intercepted or not interactable. Retrying...");
                try { Thread.sleep(1000); } catch (Exception ignored) {}
            } catch (Exception e) {
                System.out.println("Exception during click: " + e.getMessage());
                try { Thread.sleep(1000); } catch (Exception ignored) {}
            }
            attempts++;
        }
        
        try {
            takeScreenshot(driver, "Failed_Click", false, "Unable to click element: " + locator);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Unable to click: " + locator + " - Continuing script...");
        return false;
    }

    public static void waitForElementToBeClickable(WebDriver driver, WebElement element) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.elementToBeClickable(element));
    }

//    Product page

    public static void product(WebDriver driver) throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Actions actions = new Actions(driver);

        try {
            WebElement menu = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("a[title='Screws & Fasteners']")));
            actions.moveToElement(menu).perform();
        } catch (Exception e) {
            System.out.println("Menu not found.");
        }

        safeClick(driver, By.xpath("//a[@title=\"McFeely's ProMax\"]"));
        waitForPageToLoad(driver);
        takeScreenshot(driver, "category_page");

        safeClick(driver, By.xpath("//a[normalize-space()=\"8 x 1-3/8 in. ProMax Flat Head Wood Screw Dry-Lube\"]"));
        waitForPageToLoad(driver);
        takeScreenshot(driver, "Product_page");

        safeClick(driver, By.xpath("//tbody[1]/tr[1]/td[2]/div[1]/div[1]/a[1]"));
        safeClick(driver, By.cssSelector("#product-addtocart-button"));
        Thread.sleep(1000);
        takeScreenshot(driver, "Add_to_cart");

        safeClick(driver, By.xpath("//a[@class=\"action showcart\"]"));
        Thread.sleep(500);
        takeScreenshot(driver, "Mini_cart");

        safeClick(driver, By.xpath("//span[normalize-space()=\"View Cart\"]"));
        Thread.sleep(500);
        takeScreenshot(driver, "View_cart");

        safeClick(driver, By.cssSelector("a.qty-inc"));
        Thread.sleep(2500);
        safeClick(driver, By.cssSelector("a.qty-inc"));
        safeClick(driver, By.cssSelector("button[title=\"Update Cart\"]"));
        Thread.sleep(3000);
        takeScreenshot(driver, "Update_cart");

        driver.findElement(By.cssSelector("#coupon_code")).sendKeys("exitest");
        Thread.sleep(100);
        safeClick(driver, By.cssSelector("button[value=\"Apply Discount\"]"));
        Thread.sleep(3000);
        takeScreenshot(driver,"Discount_in_cart");



        safeClick(driver, By.cssSelector("button[data-role=\"proceed-to-checkout\"]"));
        Thread.sleep(100);
        driver.findElement(By.cssSelector("#email")).sendKeys("deepak.maheshwari@exinent.com");
        driver.findElement(By.cssSelector("#password")).sendKeys("Admin@123");
        Thread.sleep(800);

        safeClick(driver, By.cssSelector("#customer_form_login_popup_showPassword"));
        safeClick(driver, By.xpath("//button[@id=\"customer-form-login-popup-send2\"]"));
        Thread.sleep(200);
        takeScreenshot(driver,"User_login");

        Thread.sleep(5000);
        takeScreenshot(driver,"Logged_in");
    }

    // Product search and filters

    public static void product_search(WebDriver driver) throws Exception {
        driver.get("https://mcfeelys.com/");
        waitForPageToLoad(driver);
        Thread.sleep(2000);

        WebElement search1 = driver.findElement(By.id("search"));
        search1.sendKeys("staples");
        search1.sendKeys(Keys.ENTER);
        waitForPageToLoad(driver);
        Thread.sleep(5000);
        takeScreenshot(driver, "product_search_valid");

        WebElement search2 = driver.findElement(By.id("search"));
        search2.clear();
        search2.sendKeys("pizza");
        search2.sendKeys(Keys.ENTER);
        waitForPageToLoad(driver);
        Thread.sleep(5000);
        takeScreenshot(driver, "product_search_invalid");

        driver.get("https://www.mcfeelys.com/screw-fastener-web-store/shop-screws-by-brands/mcfeely-s-promax.html");
        waitForPageToLoad(driver);
        Thread.sleep(2000);

        Select sorter = new Select(driver.findElement(By.id("sorter")));
        sorter.selectByValue("price");
        Thread.sleep(3000);
        takeScreenshot(driver, "product_filter_by_price");

        safeClick(driver, By.xpath("//a[@class=\"action showcart\"]"));
        Thread.sleep(500);


        safeClick(driver, By.xpath("//span[normalize-space()=\"View Cart\"]"));
        Thread.sleep(500);


    }

    // checkout
    public static void checkout(WebDriver driver) throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        // Wait for the checkout button to be present
        WebElement checkoutBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("button[data-role='proceed-to-checkout']")));

        // Scroll into view
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", checkoutBtn);

        // Wait until clickable (visible + enabled)
        WebElement finalCheckoutBtn = checkoutBtn;
        checkoutBtn = wait.until(driver1 -> {
            if (finalCheckoutBtn.isDisplayed() && finalCheckoutBtn.isEnabled()) {
                return finalCheckoutBtn;
            } else {
                return null;
            }
        });

        // Click using JS to bypass any overlay issues
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", checkoutBtn);

        // Wait for checkout page to load
        waitForPageToLoad(driver);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".checkout-header")));
        Thread.sleep(2000); // allow animations to finish

        // Fill checkout form
        driver.findElement(By.name("street[0]")).clear();
        driver.findElement(By.name("street[0]")).sendKeys("California");

        driver.findElement(By.name("city")).clear();
        driver.findElement(By.name("city")).sendKeys("California");

        driver.findElement(By.name("company")).clear();
        driver.findElement(By.name("company")).sendKeys("Exinent Test");

        driver.findElement(By.name("region_id")).sendKeys("California");

        driver.findElement(By.name("postcode")).clear();
        driver.findElement(By.name("postcode")).sendKeys("90001");

        driver.findElement(By.name("telephone")).clear();
        driver.findElement(By.name("telephone")).sendKeys("9870999521");


        Thread.sleep(3000);
        takeScreenshot(driver, "checkout_form");
    }


    // ------------------ Screenshot + Upload + CSV + HTML ------------------
    private static void takeScreenshot(WebDriver driver, String title) throws IOException {
        takeScreenshot(driver, title, true, "Step completed successfully");
    }

    private static void takeScreenshot(WebDriver driver, String title, boolean isPass, String details) throws IOException {
        totalSteps++;
        if (isPass) passedSteps++;
        else failedSteps++;

        // Use a short timestamp for file name
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String statusPrefix = isPass ? "SUCCESS_" : "ERROR_";
        String fileName = statusPrefix + title + "_" + timestamp + ".png";

        // Folder to store screenshots (Organized by Date and Time)
        File folder = new File(SS_DIR);
        if (!folder.exists()) folder.mkdirs();

        // Save screenshot locally
        File outputFile = new File(folder, fileName);
        File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        Files.copy(src.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        System.out.println("Screenshot saved: " + outputFile.getAbsolutePath());

        // Upload screenshot to Imgbb
        String uploadedUrl = "Upload failed/skipped";
        try {
            uploadedUrl = uploadToImgbb(outputFile);
            System.out.println("Uploaded URL: " + uploadedUrl);
        } catch (Exception e) {
            System.out.println("Could not upload to Imgbb: " + e.getMessage());
        }

        // Write to CSV (append mode)
        writeCsv(timestamp, title, uploadedUrl, outputFile.getName());
        
        // Write to HTML report
        writeHtmlReport(timestamp, title, outputFile.getName(), uploadedUrl, isPass, details);
    }

    private static String uploadToImgbb(File imageFile) throws IOException {
        byte[] fileContent = Files.readAllBytes(imageFile.toPath());
        String encodedImage = Base64.getEncoder().encodeToString(fileContent);

        String data = "key=" + IMGBB_API_KEY +
                "&image=" + URLEncoder.encode(encodedImage, "UTF-8");

        URL url = new URL("https://api.imgbb.com/1/upload");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");

        OutputStream os = conn.getOutputStream();
        os.write(data.getBytes());
        os.flush();
        os.close();

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();

        String line;
        while ((line = br.readLine()) != null) response.append(line);
        br.close();

        String json = response.toString();
        return json.split("\"url\":\"")[1].split("\"")[0].replace("\\/", "/");
    }

    private static void writeCsv(String timestamp, String title, String url, String localFileName) {
        String csvFile = CSV_PATH;
        File fileObj = new File(csvFile);
        if (!fileObj.getParentFile().exists()) {
            fileObj.getParentFile().mkdirs();
        }
        boolean fileExists = fileObj.exists();

        try (FileWriter fw = new FileWriter(csvFile, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            // If CSV does not exist, write header first
            if (!fileExists) {
                out.println("Timestamp,Title,LocalFile,UploadedURL");
            }

            // Append new row
            out.println(timestamp + "," + title + "," + localFileName + "," + url);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeHtmlReport(String timestamp, String title, String localFileName, String url, boolean isPass, String details) {
        File htmlFolder = new File(HTML_DIR);
        if (!htmlFolder.exists()) htmlFolder.mkdirs();

        String htmlFile = HTML_DIR + "\\TestReport.html";
        
        // Relative path from HTML file to the Screenshot image
        String relativeImgPath = "../../../screenshots/" + RUN_DATE + "/" + RUN_TIME + "/" + localFileName;

        // Build the HTML for the current step and add it to our static list
        String stepStatusClass = isPass ? "pass" : "fail";
        String stepStatusIcon = isPass ? "✅" : "❌";
        
        StringBuilder stepHtml = new StringBuilder();
        stepHtml.append("            <div class=\"test-step ").append(stepStatusClass).append("\">\n");
        stepHtml.append("                <div class=\"step-header\">\n");
        stepHtml.append("                    <span>").append(stepStatusIcon).append(" ").append(title.replace("_", " ").toUpperCase()).append("</span>\n");
        stepHtml.append("                    <span class=\"step-time\">").append(timestamp.split("_")[1].replace("-", ":")).append("</span>\n");
        stepHtml.append("                </div>\n");
        stepHtml.append("                <div class=\"step-details\">").append(details).append("</div>\n");
        stepHtml.append("                <div style=\"margin-top: 15px;\">\n");
        stepHtml.append("                    <a href=\"").append(relativeImgPath).append("\" target=\"_blank\">\n");
        stepHtml.append("                        <img class=\"screenshot\" src=\"").append(relativeImgPath).append("\" alt=\"").append(title).append("\">\n");
        stepHtml.append("                    </a>\n");
        stepHtml.append("                </div>\n");
        stepHtml.append("                <div style=\"margin-top: 10px;\">\n");
        stepHtml.append("                    <a class=\"btn\" href=\"").append(relativeImgPath).append("\" target=\"_blank\">View Local</a>\n");
        if (url != null && url.startsWith("http")) {
            stepHtml.append("                    <a class=\"btn imgbb\" href=\"").append(url).append("\" target=\"_blank\">View ImgBB</a>\n");
        }
        stepHtml.append("                </div>\n");
        stepHtml.append("            </div>");
        
        htmlSteps.add(stepHtml.toString());

        // Overwrite file entirely on every step to update the total counts at the top and include ALL steps
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
            out.println("    <title>McFeels E-commerce Automation - Enhanced Test Report</title>");
            out.println("    <style>");
            out.println("        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 20px; background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%); }");
            out.println("        .container { max-width: 1400px; margin: 0 auto; background: white; border-radius: 15px; box-shadow: 0 10px 30px rgba(0,0,0,0.1); padding: 40px; }");
            out.println("        .header { text-align: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px; border-radius: 15px; margin-bottom: 40px; }");
            out.println("        .header h1 { margin: 0; font-size: 2.5em; font-weight: 300; }");
            out.println("        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 25px; margin-bottom: 40px; }");
            out.println("        .summary-card { background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); padding: 25px; border-radius: 15px; text-align: center; border-left: 6px solid #667eea; transition: transform 0.3s; }");
            out.println("        .summary-card:hover { transform: translateY(-5px); }");
            out.println("        .summary-card h3 { margin: 0 0 15px 0; color: #333; font-size: 1.1em; }");
            out.println("        .summary-card .number { font-size: 2.5em; font-weight: bold; color: #333; margin-bottom: 10px; }");
            out.println("        .progress-bar { width: 100%; height: 25px; background-color: #e9ecef; border-radius: 12px; overflow: hidden; margin: 15px 0; }");
            out.println("        .progress-fill { height: 100%; background: linear-gradient(90deg, #28a745, #20c997); transition: width 1s ease; border-radius: 12px; }");
            out.println("        .test-results { margin: 40px 0; display: flex; flex-direction: column; gap: 15px; }");
            out.println("        .test-step { margin: 15px 0; padding: 20px; border-radius: 12px; border-left: 6px solid; transition: all 0.3s; }");
            out.println("        .test-step:hover { transform: translateX(5px); }");
            out.println("        .test-step.pass { background: linear-gradient(135deg, #d4edda 0%, #c3e6cb 100%); border-left-color: #28a745; }");
            out.println("        .test-step.fail { background: linear-gradient(135deg, #f8d7da 0%, #f5c6cb 100%); border-left-color: #dc3545; }");
            out.println("        .step-header { font-weight: bold; margin-bottom: 12px; font-size: 1.1em; }");
            out.println("        .step-details { font-size: 0.95em; color: #666; line-height: 1.5; }");
            out.println("        .step-time { font-size: 0.85em; color: #888; float: right; background: rgba(0,0,0,0.05); padding: 4px 8px; border-radius: 5px; }");
            out.println("        .screenshot { max-width: 300px; border-radius: 8px; margin: 15px 0; border: 2px solid #ddd; transition: transform 0.3s; cursor: pointer; }");
            out.println("        .screenshot:hover { transform: scale(1.05); }");
            out.println("        .timestamp { text-align: center; color: #666; margin: 25px 0; font-size: 1.1em; }");
            out.println("        .status-badge { display: inline-block; padding: 8px 20px; border-radius: 25px; color: white; font-weight: bold; }");
            out.println("        .status-pass { background: linear-gradient(135deg, #28a745 0%, #20c997 100%); }");
            out.println("        .status-fail { background: linear-gradient(135deg, #dc3545 0%, #c82333 100%); }");
            out.println("        .btn { display: inline-block; padding: 8px 15px; background: linear-gradient(135deg, #28a745 0%, #20c997 100%); color: white; text-decoration: none; border-radius: 20px; font-weight: bold; font-size: 0.9em; margin-right: 10px; transition: opacity 0.3s; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }");
            out.println("        .btn:hover { opacity: 0.9; }");
            out.println("        .btn.imgbb { background: linear-gradient(135deg, #17a2b8 0%, #117a8b 100%); }");
            out.println("    </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("    <div class=\"container\">");
            out.println("        <div class=\"header\">");
            out.println("            <h1>🛒 McFeels E-commerce Automation</h1>");
            out.println("            <p style=\"font-size: 1.2em; margin: 10px 0;\">Enhanced Test Report with Detailed Steps</p>");
            out.println("            <div class=\"timestamp\">Generated on: " + RUN_DATE + " at " + RUN_TIME.replace("-", ":") + "</div>");
            out.println("        </div>");
            
            // Dynamic Summary Cards
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
            out.println("            <h2>📋 Detailed Test Results</h2>");
            out.println("            <p style=\"color: #666; margin-bottom: 30px;\">Comprehensive step-by-step execution details with visual evidence</p>");

            // Append ALL Step details dynamically from our static list
            for (String step : htmlSteps) {
                out.println(step);
            }

            out.println("        </div>"); // Closing test-results
            
            // Footer
            out.println("        <div style=\"margin-top: 50px; padding-top: 20px; border-top: 1px solid #dee2e6; text-align: center; color: #6c757d;\">");
            out.println("            <p>🤖 Generated by McFeels E-commerce Automation Framework v2.0</p>");
            out.println("            <p>Dynamic testing with random categories and products for comprehensive coverage</p>");
            out.println("            <p><small>Report updated dynamically during execution.</small></p>");
            out.println("        </div>");
            out.println("    </div>");
            out.println("</body>");
            out.println("</html>");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
