package nutridyn;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NutriDyn UI flows split into steps for TestNG (run in order via {@code dependsOnMethods}).
 */
public final class NutridynFlow {

    private NutridynFlow() {
    }

    /** Full run: delegates to all phase methods (for {@link NutridynLauncher}). */
    public static void runEndToEnd(WebDriver driver) throws IOException, InterruptedException {
        openHomeAndAcceptCookies(driver);
        loginPatient(driver);
        // Browse a few category listing pages (from sitemap) before adding products — proves navigation + coverage.
        browseRandomCategoriesFromSitemap(driver);
        addRandomSitemapProductsToCart(driver);
        openCartAndCheckout(driver);
        goHomeViaHeaderLogoAfterCheckout(driver);
        openAccountThenHomeForPractitionerLogin(driver);
        loginPractitioner(driver);
        practitionerOrdersAndSubscriptions(driver);
        practitionerListsPatientsAddress(driver);
        practitionerNutriScripts(driver);
        practitionerConnectGeneticsAndLinks(driver);
        practitionerHomeLogoAndLogout(driver);
    }

    public static void openHomeAndAcceptCookies(WebDriver driver) throws IOException, InterruptedException {
        driver.navigate().to(NutridynConfig.BASE_URL);
        Thread.sleep(1000);
        System.out.println("Site is opened");
        NutridynReporting.takeScreenshot(driver, "Homepage");
        Thread.sleep(1000);

        if (driver.findElements(By.xpath("//span[contains(text(),'Accept')]")).size() > 0) {
            driver.findElement(By.xpath("//span[contains(text(),'Accept')]")).click();
            Thread.sleep(2000);
            System.out.println("Cookie accepted");
            NutridynReporting.takeScreenshot(driver, "Cookie_Accepted");
        } else {
            System.out.println("Cookie accept button not found");
        }
    }

    public static void loginPatient(WebDriver driver) throws IOException, InterruptedException {
        if (driver.findElements(By.xpath("//*[@id='header-account-1']/ul/li/a")).size() > 0) {
            driver.findElement(By.xpath("//*[@id='header-account-1']/ul/li/a")).click();
            Thread.sleep(3000);
            System.out.println("Login page opened");
            NutridynReporting.takeScreenshot(driver, "Login_Page");
        } else {
            System.out.println("Login link not found");
        }

        if (driver.findElements(By.id("email")).size() > 0) {
            driver.findElement(By.id("email")).sendKeys(NutridynConfig.PATIENT_EMAIL);
            System.out.println("Username entered");
            Thread.sleep(1000);
        } else {
            System.out.println("Username field not found");
        }
        if (driver.findElements(By.id("pass")).size() > 0) {
            driver.findElement(By.id("pass")).sendKeys(NutridynConfig.PATIENT_PASSWORD);
            System.out.println("Password is entered");
            Thread.sleep(2000);
        } else {
            System.out.println("Password field not found");
        }
        if (driver.findElements(By.name("send")).size() > 0) {
            driver.findElement(By.name("send")).click();
            System.out.println("Patient account login successful");
            Thread.sleep(2000);
            NutridynReporting.takeScreenshot(driver, "Patient_Login_Success");
        } else {
            System.out.println("Login button not found");
        }
    }

    /**
     * Open 3 or 4 random storefront category URLs from the sitemap (logged-in patient session).
     * Each page gets a screenshot for the report; we do not add category pages to the cart.
     */
    public static void browseRandomCategoriesFromSitemap(WebDriver driver) throws IOException, InterruptedException {
        // nextInt(3, 5) → 3 or 4 distinct category URLs
        int numCategories = ThreadLocalRandom.current().nextInt(3, 5);
        List<String> categoryUrls = NutridynSitemap.loadCategoryUrlsFromSitemap(NutridynConfig.SITEMAP_URL, numCategories);
        System.out.println("Sitemap: browsing " + categoryUrls.size() + " category URL(s) (requested up to " + numCategories + "):");
        for (String u : categoryUrls) {
            System.out.println("  - " + u);
        }

        if (categoryUrls.isEmpty()) {
            System.out.println("No category URLs from sitemap — skipping category browse block.");
            return;
        }

        for (int i = 0; i < categoryUrls.size(); i++) {
            String curl = categoryUrls.get(i);
            driver.navigate().to(curl);
            Thread.sleep(2500);
            System.out.println("Opened category " + (i + 1) + ": " + curl);
            String slug = NutridynSitemap.categoryUrlSlug(curl);
            NutridynReporting.takeScreenshot(
                    driver,
                    "Category_" + (i + 1) + "_" + slug,
                    true,
                    "Category page from sitemap (browse only, no add-to-cart).",
                    curl);
        }
    }

    /**
     * Pick 2 or 3 product detail URLs from the sitemap, open each PDP, and click Add to Cart when the button exists.
     */
    public static void addRandomSitemapProductsToCart(WebDriver driver) throws IOException, InterruptedException {
        // Random number of products: nextInt(2, 4) → 2 or 3
        int numProducts = ThreadLocalRandom.current().nextInt(2, 4);
        List<String> productUrls = NutridynSitemap.loadProductUrlsFromSitemap(NutridynConfig.SITEMAP_URL, numProducts);
        System.out.println("Sitemap: using " + productUrls.size() + " product URL(s) (requested up to " + numProducts + "):");
        for (String u : productUrls) {
            System.out.println("  - " + u);
        }

        if (productUrls.isEmpty()) {
            System.out.println("No product URLs from sitemap — skipping add-to-cart block.");
        } else {
            // One iteration per PDP: navigate → screenshot → optional #product-addtocart-button click
            for (int i = 0; i < productUrls.size(); i++) {
                String purl = productUrls.get(i);
                driver.navigate().to(purl);
                Thread.sleep(2500);
                System.out.println("Opened product " + (i + 1) + ": " + purl);
                NutridynReporting.takeScreenshot(
                        driver,
                        "Product_Page_" + (i + 1) + "_" + NutridynSitemap.productUrlSlug(purl),
                        true,
                        "Product detail page opened from sitemap.",
                        purl);

                if (driver.findElements(By.id("product-addtocart-button")).size() > 0) {
                    driver.findElement(By.id("product-addtocart-button")).click();
                    System.out.println("Product " + (i + 1) + " added to cart");
                    Thread.sleep(1000);
                    NutridynReporting.takeScreenshot(
                            driver,
                            "Product_Added_To_Cart_" + (i + 1),
                            true,
                            "After Add to Cart for this PDP.",
                            purl);
                } else {
                    System.out.println("Add to Cart not found for product " + (i + 1) + " — may be OOS or not a simple product.");
                }
            }
        }
    }

    public static void openCartAndCheckout(WebDriver driver) throws IOException, InterruptedException {
        if (driver.findElements(By.xpath("//*[@id='minicart']/div[1]/span/span[1]")).size() > 0) {
            driver.findElement(By.xpath("//*[@id='minicart']/div[1]/span/span[1]")).click();
            Thread.sleep(2000);
            System.out.println("Cart page opened");
            NutridynReporting.takeScreenshot(driver, "Cart_Page");
        } else {
            System.out.println("Mini cart icon not found");
        }

        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.scrollBy(0, 300);");
        Thread.sleep(2000);

        List<WebElement> checkoutBtns = driver.findElements(
                By.xpath("//*[@id='maincontent']/div[3]/div/div[6]/div[1]/div[2]/ul/li/button"));
        if (!checkoutBtns.isEmpty()) {
            WebElement checkoutBtn = checkoutBtns.get(0);
            JavascriptExecutor jex = (JavascriptExecutor) driver;
            jex.executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", checkoutBtn);
            Thread.sleep(500);
            try {
                WebElement toClick = new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.elementToBeClickable(checkoutBtn));
                toClick.click();
            } catch (ElementClickInterceptedException | TimeoutException e) {
                jex.executeScript("arguments[0].click();", checkoutBtn);
            }
            Thread.sleep(5000);
            System.out.println("Checkout page opened");
            NutridynReporting.takeScreenshot(driver, "Checkout_Page");
        } else {
            System.out.println("Checkout button not found");
        }
    }

    public static void goHomeViaHeaderLogoAfterCheckout(WebDriver driver) throws IOException, InterruptedException {
        if (driver.findElements(By.xpath("//*[@id='header_logo']/a/img")).size() > 0) {
            driver.findElement(By.xpath("//*[@id='header_logo']/a/img")).click();
            Thread.sleep(2000);
            System.out.println("Home page opened");
            NutridynReporting.takeScreenshot(driver, "Home_After_Checkout");
        } else {
            System.out.println("Header logo not found");
        }
    }

    public static void openAccountThenHomeForPractitionerLogin(WebDriver driver) throws InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement accountLink = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id='header-account-1']/ul/li/a")));
        accountLink.click();
        System.out.println("Account page opened");

        WebElement homeLogo = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id='header_logo']/a")));
        homeLogo.click();
        System.out.println("Home page opened");
    }

    public static void loginPractitioner(WebDriver driver) throws IOException, InterruptedException {
        if (driver.findElements(By.xpath("//*[@id='header-account-1']/ul/li/a")).size() > 0) {
            driver.findElement(By.xpath("//*[@id='header-account-1']/ul/li/a")).click();
            Thread.sleep(1000);
            System.out.println("Account login page opened");
        } else {
            System.out.println("Account link not found");
        }

        if (driver.findElements(By.id("email")).size() > 0) {
            driver.findElement(By.id("email")).sendKeys(NutridynConfig.PRACTITIONER_EMAIL);
            System.out.println("Practitioner login username entered");
            Thread.sleep(1000);
        } else {
            System.out.println("Username field not found");
        }

        if (driver.findElements(By.id("pass")).size() > 0) {
            driver.findElement(By.id("pass")).sendKeys(NutridynConfig.PRACTITIONER_PASSWORD);
            System.out.println("Practitioner login password is entered");
            Thread.sleep(2000);
        } else {
            System.out.println("Password field not found");
        }

        if (driver.findElements(By.name("send")).size() > 0) {
            driver.findElement(By.name("send")).click();
            System.out.println("Practitioner account login successful");
            Thread.sleep(2000);
            NutridynReporting.takeScreenshot(driver, "Practitioner_Login_Success");
        } else {
            System.out.println("Login button not found");
        }
    }

    public static void practitionerOrdersAndSubscriptions(WebDriver driver) throws IOException, InterruptedException {
        WebElement myOrders = driver.findElement(
                By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[2]/a/span"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", myOrders);
        System.out.println("My Orders page is opened");
        NutridynReporting.takeScreenshot(driver, "My_Orders");
        Thread.sleep(1000);

        if (driver.findElements(By.xpath("//*[@id=\"my-orders-table\"]/tbody/tr[1]/td[6]/a[1]/span")).size() > 0) {
            driver.findElement(By.xpath("//*[@id=\"my-orders-table\"]/tbody/tr[1]/td[6]/a[1]/span")).click();
            Thread.sleep(2000);
            System.out.println("First order view page is opened");
            NutridynReporting.takeScreenshot(driver, "First_Order_View");
        } else {
            System.out.println("First order view link not found");
        }

        if (driver.findElements(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[3]/a/span")).size() > 0) {
            driver.findElement(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[3]/a/span")).click();
            Thread.sleep(2000);
            System.out.println("My Subscriptions page is opened");
            NutridynReporting.takeScreenshot(driver, "My_Subscriptions");
        } else {
            System.out.println("My Subscriptions link not found");
        }

        if (driver.findElements(By.xpath("//*[@id=\"my-orders-table\"]/tbody/tr[1]/td[6]/a/span")).size() > 0) {
            driver.findElement(By.xpath("//*[@id=\"my-orders-table\"]/tbody/tr[1]/td[6]/a/span")).click();
            Thread.sleep(2000);
            System.out.println("Subscription view page is opened");
            NutridynReporting.takeScreenshot(driver, "Subscription_View");
        } else {
            System.out.println("Subscription view link not found");
        }
    }

    public static void practitionerListsPatientsAddress(WebDriver driver) throws IOException, InterruptedException {
        if (driver.findElements(By.xpath("//*[@id='maincontent']/div[2]/div[2]/div/div/div/ul[1]/li[4]/a/span")).size() > 0) {
            driver.findElement(By.xpath("//*[@id='maincontent']/div[2]/div[2]/div/div/div/ul[1]/li[4]/a/span")).click();
            Thread.sleep(2000);
            System.out.println("List page is opened");
            NutridynReporting.takeScreenshot(driver, "List_Page");
        } else {
            System.out.println("List page link not found");
        }

        if (driver.findElements(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[5]/a/span")).size() > 0) {
            driver.findElement(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[5]/a/span")).click();
            Thread.sleep(2000);
            System.out.println("My Patients page is opened");
            NutridynReporting.takeScreenshot(driver, "My_Patients");
        } else {
            System.out.println("My Patients link not found");
        }

        if (driver.findElements(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[6]/a/span")).size() > 0) {
            driver.findElement(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[6]/a/span")).click();
            Thread.sleep(2000);
            System.out.println("Address Book page is opened");
            NutridynReporting.takeScreenshot(driver, "Address_Book");
        } else {
            System.out.println("Address Book link not found");
        }

        JavascriptExecutor js3 = (JavascriptExecutor) driver;
        js3.executeScript("window.scrollBy(0, 400);");
    }

    public static void practitionerNutriScripts(WebDriver driver) throws IOException, InterruptedException {
        if (driver.findElements(By.xpath("//a//span[text()='NutriScripts']")).size() > 0) {
            driver.findElement(By.xpath("//a//span[text()='NutriScripts']")).click();
            System.out.println("NutriScripts clicked");
            NutridynReporting.takeScreenshot(driver, "NutriScripts");
        } else {
            System.out.println("NutriScripts not found");
        }
        Thread.sleep(2000);

        JavascriptExecutor js1 = (JavascriptExecutor) driver;
        js1.executeScript("window.scrollBy(0, 200);");

        List<WebElement> viewNutriLinks = driver.findElements(By.xpath("//a[contains(normalize-space(),'View NutriScript')]"));
        if (!viewNutriLinks.isEmpty()) {
            WebElement viewNutri = viewNutriLinks.get(0);
            JavascriptExecutor jex = (JavascriptExecutor) driver;
            jex.executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", viewNutri);
            Thread.sleep(500);
            try {
                WebElement toClick = new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.elementToBeClickable(viewNutri));
                toClick.click();
            } catch (ElementClickInterceptedException | TimeoutException e) {
                jex.executeScript("arguments[0].click();", viewNutri);
            }
            System.out.println("View NutriScript clicked");
            NutridynReporting.takeScreenshot(driver, "View_NutriScript");
        } else {
            System.out.println("View NutriScript not found");
        }

        Thread.sleep(2000);
        JavascriptExecutor js2 = (JavascriptExecutor) driver;
        js2.executeScript("window.scrollBy(0, 100);");
        Thread.sleep(2000);
    }

    public static void practitionerConnectGeneticsAndLinks(WebDriver driver) throws IOException, InterruptedException {
        if (driver.findElements(By.xpath("//span[contains(text(),'3X4 Genetics')]")).size() > 0) {
            driver.findElement(By.xpath("//span[contains(text(),'3X4 Genetics')]")).click();
            System.out.println("3X4 Genetics clicked");
            NutridynReporting.takeScreenshot(driver, "3X4_Genetics");
        } else {
            System.out.println("3X4 Genetics not found");
        }
        Thread.sleep(2000);

        if (driver.findElements(By.xpath("//span[text()='NutriDyn Connect']")).size() > 0) {
            driver.findElement(By.xpath("//span[text()='NutriDyn Connect']")).click();
            System.out.println("NutriDyn Connect clicked");
            NutridynReporting.takeScreenshot(driver, "NutriDyn_Connect");
        } else {
            System.out.println("NutriDyn Connect not found");
        }
        Thread.sleep(2000);

        if (driver.findElements(By.xpath("//a[normalize-space()='NutriDyn Connect Pro']")).size() > 0) {
            driver.findElement(By.xpath("//a[normalize-space()='NutriDyn Connect Pro']")).click();
            System.out.println("NutriDyn Connect Pro clicked");
            NutridynReporting.takeScreenshot(driver, "NutriDyn_Connect_Pro");
        } else {
            System.out.println("NutriDyn Connect Pro not found");
        }
        Thread.sleep(2000);

        JavascriptExecutor js7 = (JavascriptExecutor) driver;
        js7.executeScript("window.scrollBy(0, 200);");
        Thread.sleep(2000);

        if (driver.findElements(By.xpath("//a[contains(@href,'connectpro/applet')]")).size() > 0) {
            driver.findElement(By.xpath("//a[contains(@href,'connectpro/applet')]")).click();
            System.out.println("NutriDyn Connect Applet clicked");
            NutridynReporting.takeScreenshot(driver, "Connect_Applet");
        } else {
            System.out.println("NutriDyn Connect Applet not found");
        }
        Thread.sleep(2000);

        JavascriptExecutor js4 = (JavascriptExecutor) driver;
        js4.executeScript("window.scrollBy(0, 300);");
        Thread.sleep(2000);

        if (driver.findElements(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[10]/ul/li[3]/a")).size() > 0) {
            driver.findElement(By.xpath("//*[@id=\"maincontent\"]/div[2]/div[2]/div/div/div/ul[1]/li[10]/ul/li[3]/a")).click();
            System.out.println("NutriDyn Connect Links clicked");
            NutridynReporting.takeScreenshot(driver, "Connect_Links");
        } else {
            System.out.println("NutriDyn Connect Links not found");
        }
        Thread.sleep(2000);

        JavascriptExecutor js5 = (JavascriptExecutor) driver;
        js5.executeScript("window.scrollBy(0, -400);");
        Thread.sleep(2000);

        if (driver.findElements(By.xpath("//a//span[text()='NutriScripts']")).size() > 0) {
            driver.findElement(By.xpath("//a//span[text()='NutriScripts']")).click();
            System.out.println("NutriScripts clicked again");
        } else {
            System.out.println("NutriScripts not found");
        }
    }

    public static void practitionerHomeLogoAndLogout(WebDriver driver) throws IOException, InterruptedException {
        WebElement logo = driver.findElement(By.xpath("//*[@id='header_logo']/a"));
        JavascriptExecutor js51 = (JavascriptExecutor) driver;
        js51.executeScript("arguments[0].click();", logo);
        System.out.println("Home page opened again");
        NutridynReporting.takeScreenshot(driver, "Home_Again");

        if (driver.findElements(By.xpath("//a[normalize-space()='Logout']")).size() > 0) {
            WebElement logout = driver.findElement(By.xpath("//a[normalize-space()='Logout']"));
            JavascriptExecutor js11 = (JavascriptExecutor) driver;
            js11.executeScript("arguments[0].click();", logout);
            System.out.println("Logout clicked using JS");
            NutridynReporting.takeScreenshot(driver, "Logout");
        } else {
            System.out.println("Logout link not found");
        }

        Thread.sleep(2000);
    }
}
