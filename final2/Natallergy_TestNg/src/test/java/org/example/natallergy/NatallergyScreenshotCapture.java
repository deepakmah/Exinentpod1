package org.example.natallergy;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes the PNG file used by {@link NatallergyReporting}. The saved image is the <b>full scrollable page</b> when
 * the driver is {@link ChromeDriver} (CDP). The HTML report shows only a scrollable preview window; open the file
 * for the uncropped capture. Non-Chrome drivers get a viewport-only shot.
 */
public final class NatallergyScreenshotCapture {

    /** Full-page PNG on disk for Chrome; viewport fallback otherwise. */
    public static void saveCurrentPageAsPng(WebDriver driver, File outputFile) throws IOException {
        byte[] pngBytes = captureCurrentPagePngBytes(driver);
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Files.write(outputFile.toPath(), pngBytes);
    }

    private static byte[] captureCurrentPagePngBytes(WebDriver driver) throws IOException {
        if (driver instanceof ChromeDriver) {
            Map<String, Object> params = new HashMap<>();
            params.put("format", "png");
            params.put("captureBeyondViewport", true);
            params.put("fromSurface", true);
            Map<String, Object> result = ((ChromeDriver) driver).executeCdpCommand("Page.captureScreenshot", params);
            String data = (String) result.get("data");
            return Base64.getDecoder().decode(data);
        }
        File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        return Files.readAllBytes(src.toPath());
    }

    private NatallergyScreenshotCapture() {
    }
}
