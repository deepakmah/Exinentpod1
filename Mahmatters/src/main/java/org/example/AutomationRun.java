package org.example;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Snapshot of one test run: where files go and how the HTML report is built up step by step.
 */
public final class AutomationRun {

    /** Root folder for screenshots, html reports, and CSV (change if you move output). */
    private static final String OUTPUT_BASE =
            "C:\\Users\\deepa\\Documents\\Automation\\Mahmatters\\";

    /** yyyy-MM-dd folder segment. */
    public final String runDate;
    /** HH-mm-ss folder segment (unique per run same day). */
    public final String runTime;
    /** PNG screenshots for this run. */
    public final String ssDir;
    /** TestReport.html for this run. */
    public final String htmlDir;
    /** One CSV appended across runs (timestamped rows). */
    public final String csvPath;
    /** Wall-clock start for “Test duration” in HTML. */
    public final String startTime;

    /** Counters drive summary cards in TestReport.html. */
    public int totalSteps;
    public int passedSteps;
    public int failedSteps;
    /** One HTML block per screenshot step; rewritten into the report file after each step. */
    public final List<String> htmlSteps = new ArrayList<>();

    private AutomationRun(String runDate, String runTime, String ssDir, String htmlDir, String csvPath,
            String startTime) {
        this.runDate = runDate;
        this.runTime = runTime;
        this.ssDir = ssDir;
        this.htmlDir = htmlDir;
        this.csvPath = csvPath;
        this.startTime = startTime;
    }

    /** Builds paths using current date/time so each execution gets its own screenshot/html subfolders. */
    public static AutomationRun create() {
        String rd = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String rt = new SimpleDateFormat("HH-mm-ss").format(new Date());
        String st = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        return new AutomationRun(
                rd,
                rt,
                OUTPUT_BASE + "screenshots\\" + rd + "\\" + rt,
                OUTPUT_BASE + "html\\" + rd + "\\" + rt,
                OUTPUT_BASE + "Mahmatters.csv",
                st);
    }
}
