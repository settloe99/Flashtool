package org.flashtool.gui;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.flashtool.gui.tools.FastBootToolBoxJob;
import org.flashtool.gui.tools.SearchFastbootJob;
import org.flashtool.jna.adb.FastbootUtility;

/**
 * Waits for a fastboot device, with manual connection guidance and the
 * existing ADB-to-fastboot reboot action.
 */
@Slf4j
public class WaitDeviceForFastboot extends Dialog {

    protected Object result;
    protected Shell shlWaitForFastbootmode;
    protected SearchFastbootJob job;
    private Label statusLabel;

    public WaitDeviceForFastboot(Shell parent, int style) {
        super(parent, style);
        setText("Wait for Fastboot mode");
    }

    public Object open() {
        // Preserve the tested fast path: do not show the dialog unnecessarily.
        if (FastbootUtility.getDevices().hasMoreElements()) {
            log.info("Fastboot device already connected");
            return "OK";
        }

        result = "Canceled";
        createContents();
        Display display = getParent().getDisplay();
        job = new SearchFastbootJob("Search Fastboot Job");

        shlWaitForFastbootmode.addListener(SWT.Close, event -> {
            job.stopSearch();
            result = "Canceled";
        });

        shlWaitForFastbootmode.open();
        shlWaitForFastbootmode.layout();
        job.schedule();

        // Keep the existing non-blocking check and tested job-result semantics.
        display.timerExec(250, new Runnable() {
            @Override
            public void run() {
                if (shlWaitForFastbootmode.isDisposed()) {
                    return;
                }

                IStatus completed = job.getResult();
                if (completed != null && job.getState() == Job.NONE) {
                    result = completed.isOK() ? "OK" : "Canceled";
                    if (completed.isOK()) {
                        log.info("Fastboot device detected");
                    }
                    shlWaitForFastbootmode.dispose();
                } else {
                    display.timerExec(250, this);
                }
            }
        });

        while (!shlWaitForFastbootmode.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        return result;
    }

    private void createContents() {
        shlWaitForFastbootmode = new Shell(getParent(), getStyle());
        shlWaitForFastbootmode.setText("Connect Xperia in Fastboot Mode");

        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 24;
        layout.marginHeight = 20;
        layout.verticalSpacing = 10;
        shlWaitForFastbootmode.setLayout(layout);

        // Reuse the platform font family, making only the headings bolder.
        FontData[] headingData = shlWaitForFastbootmode.getFont().getFontData();
        for (FontData data : headingData) {
            data.setStyle(data.getStyle() | SWT.BOLD);
        }
        Font sectionFont = new Font(shlWaitForFastbootmode.getDisplay(), headingData);

        FontData[] titleData = shlWaitForFastbootmode.getFont().getFontData();
        for (FontData data : titleData) {
            data.setStyle(data.getStyle() | SWT.BOLD);
            data.setHeight(data.getHeight() + 3);
        }
        Font titleFont = new Font(shlWaitForFastbootmode.getDisplay(), titleData);
        shlWaitForFastbootmode.addListener(SWT.Dispose, event -> {
            titleFont.dispose();
            sectionFont.dispose();
        });

        Label title = new Label(shlWaitForFastbootmode, SWT.NONE);
        title.setText("Connect your Xperia");
        title.setFont(titleFont);
        title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        statusLabel = new Label(shlWaitForFastbootmode, SWT.WRAP);
        statusLabel.setText("Status: Waiting for a Fastboot device...");
        GridData statusData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        statusData.widthHint = 460;
        statusLabel.setLayoutData(statusData);

        addSeparator();

        Label automaticTitle = new Label(shlWaitForFastbootmode, SWT.NONE);
        automaticTitle.setText("Connect automatically");
        automaticTitle.setFont(sectionFont);

        Label adbInstructions = new Label(shlWaitForFastbootmode, SWT.WRAP);
        adbInstructions.setText(
                "If Android is running with USB debugging enabled, Flashtool "
                + "can restart your phone in Fastboot mode.");
        GridData adbData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        adbData.widthHint = 460;
        adbInstructions.setLayoutData(adbData);

        Button btnRebootAdb = new Button(shlWaitForFastbootmode, SWT.PUSH);
        btnRebootAdb.setText("Reboot via ADB");
        btnRebootAdb.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        btnRebootAdb.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                btnRebootAdb.setEnabled(false);
                statusLabel.setText("Status: Requesting Fastboot reboot via ADB...");
                shlWaitForFastbootmode.layout(true, true);
                log.info("Requesting fastboot reboot via ADB");

                FastBootToolBoxJob rebootJob =
                        new FastBootToolBoxJob("Reboot fastboot via ADB");
                rebootJob.setAction("doRebootFastbootViaAdb");
                rebootJob.schedule();
            }
        });

        addSeparator();

        Label manualTitle = new Label(shlWaitForFastbootmode, SWT.NONE);
        manualTitle.setText("Connect manually");
        manualTitle.setFont(sectionFont);

        Label manualInstructions = new Label(shlWaitForFastbootmode, SWT.WRAP);
        manualInstructions.setText(
                "1. Disconnect USB and turn off the phone.\n"
                + "2. On most Xperia models, hold Volume Up while connecting USB.\n"
                + "3. Release when the notification LED turns blue.");
        GridData manualData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        manualData.widthHint = 460;
        manualInstructions.setLayoutData(manualData);

        Label olderPhones = new Label(shlWaitForFastbootmode, SWT.WRAP);
        olderPhones.setText(
                "Some older Xperia models use a different button (for example, "
                + "Menu on certain 2011 models). Check your model's instructions "
                + "if Volume Up doesn't work.");
        GridData olderData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        olderData.widthHint = 460;
        olderPhones.setLayoutData(olderData);

        addSeparator();

        Composite footer = new Composite(shlWaitForFastbootmode, SWT.NONE);
        GridLayout footerLayout = new GridLayout(1, false);
        footerLayout.marginWidth = 0;
        footerLayout.marginHeight = 0;
        footer.setLayout(footerLayout);
        footer.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

        Button btnCancel = new Button(footer, SWT.PUSH);
        btnCancel.setText("Cancel");
        GridData cancelData = new GridData(SWT.FILL, SWT.CENTER, false, false);
        cancelData.widthHint = 90;
        btnCancel.setLayoutData(cancelData);
        btnCancel.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                // SWT.Close keeps the tested background-search cancellation path.
                shlWaitForFastbootmode.close();
            }
        });

        shlWaitForFastbootmode.pack();
        shlWaitForFastbootmode.setMinimumSize(shlWaitForFastbootmode.getSize());
    }

    private void addSeparator() {
        Label separator = new Label(shlWaitForFastbootmode,
                SWT.SEPARATOR | SWT.HORIZONTAL);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

}
