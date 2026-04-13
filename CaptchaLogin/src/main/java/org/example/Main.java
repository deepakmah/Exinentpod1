package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        WebDriver driver = new ChromeDriver();

        try {
            driver.get("https://natlallergy-dev.99stockpics.com/customer/account/login");

            var form = driver.findElement(By.cssSelector("fieldset.fieldset.login"));
            form.findElement(By.id("email")).sendKeys("raviteja@exinent.com");
            form.findElement(By.id("pass")).sendKeys("Test@123");

            Thread.sleep(10000);

            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("jQuery('[name=\"recaptcha-validate-\"]').prop('checked', true);");
            js.executeScript("jQuery('.g-recaptcha-response').val('dassfsd');");


            form.findElement(By.xpath(".//button[contains(normalize-space(.),'Sign In')]")).click();

            Thread.sleep(5000);
            System.out.println("successful Login");
        } finally {
//            driver.quit();
        }
    }
}
