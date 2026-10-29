package org.flashtool.gui;

import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.flashtool.gui.tools.DevicesSyncJob;
import org.flashtool.gui.tools.WidgetTask;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WaitForDevicesSync extends Dialog {

	protected Object result;
	protected Shell shlWaiForDevicesSync;
	protected boolean canClose = false;
	protected Dialog mydial;

    private Label statusLabel;
    private Runnable startupContinuation;

	/**
	 * Create the dialog.
	 * @param parent
	 * @param style
	 */
	public WaitForDevicesSync(Shell parent, int style) {
		super(parent, style);
		setText("Syncing devices from Github");
		mydial = this;
	}

	/**
	 * Open the dialog.
	 * @return the result
	 */

public Object open() {
    return open(null);
}

public Object open(Runnable continuation) {
    startupContinuation = continuation;

    createContents();

    shlWaiForDevicesSync.open();
    shlWaiForDevicesSync.layout();

    Display display = getParent().getDisplay();

    while (!shlWaiForDevicesSync.isDisposed()) {
        if (!display.readAndDispatch()) {
            display.sleep();
        }
    }

    return result;
}

public void setStatus(String message) {
    if (shlWaiForDevicesSync == null ||
        shlWaiForDevicesSync.isDisposed() ||
        statusLabel == null ||
        statusLabel.isDisposed()) {
        return;
    }

    statusLabel.setText(message);
    shlWaiForDevicesSync.layout(true, true);

    // Paint the new status before the next operation.
    statusLabel.redraw();
    statusLabel.update();
}

public void finishStartup() {
    if (shlWaiForDevicesSync != null &&
        !shlWaiForDevicesSync.isDisposed()) {

        canClose = true;
        shlWaiForDevicesSync.dispose();
    }
}

	/**
	 * Create contents of the dialog.
	 */
private void createContents() {

    shlWaiForDevicesSync = new Shell(getParent(), getStyle());


String windowTitle = startupContinuation == null
        ? "Updating device definitions"
        : "Starting Flashtool";

shlWaiForDevicesSync.setText(windowTitle);
shlWaiForDevicesSync.setSize(420, 105);

Display display = shlWaiForDevicesSync.getDisplay();

// Compact layout.
GridLayout layout = new GridLayout(1, false);
layout.marginWidth = 18;
layout.marginHeight = 14;
layout.verticalSpacing = 8;
shlWaiForDevicesSync.setLayout(layout);

// Heading row containing icon and title.
Composite headingRow = new Composite(
    shlWaiForDevicesSync, SWT.NONE
);

GridLayout headingLayout = new GridLayout(2, false);
headingLayout.marginWidth = 0;
headingLayout.marginHeight = 0;
headingLayout.horizontalSpacing = 8;
headingRow.setLayout(headingLayout);

headingRow.setLayoutData(
    new GridData(SWT.FILL, SWT.CENTER, true, false)
);


// Small monochrome phone icon drawn with SWT.
Canvas icon = new Canvas(headingRow, SWT.NONE);
icon.setLayoutData(new GridData(20, 24));
icon.setBackground(headingRow.getBackground());

icon.addPaintListener(event -> {
    GC gc = event.gc;

    gc.setAntialias(SWT.ON);
    gc.setForeground(headingRow.getForeground());
    gc.setLineWidth(2);

    // Phone outline.
    gc.drawRoundRectangle(4, 1, 12, 20, 3, 3);

    // Speaker and home indicator.
    gc.drawLine(8, 4, 12, 4);
    gc.drawPoint(10, 18);
});

// Heading.
Label heading = new Label(headingRow, SWT.NONE);
heading.setText(windowTitle);

FontData[] titleData = heading.getFont().getFontData();

for (FontData data : titleData) {
    data.setHeight(12);
    data.setStyle(SWT.BOLD);
}

Font titleFont = new Font(display, titleData);
heading.setFont(titleFont);

// Secondary status message.
statusLabel = new Label(shlWaiForDevicesSync, SWT.NONE);
statusLabel.setText("Checking for device definition updates...");
statusLabel.setLayoutData(
    new GridData(SWT.FILL, SWT.CENTER, true, false)
);

statusLabel.setForeground(
    display.getSystemColor(SWT.COLOR_DARK_GRAY)
);

// Slightly smaller status text.
FontData[] statusData = statusLabel.getFont().getFontData();

for (FontData data : statusData) {
    data.setHeight(11);
}

Font statusFont = new Font(display, statusData);
statusLabel.setFont(statusFont);

// Thin animated progress indicator.
ProgressBar progress = new ProgressBar(
    shlWaiForDevicesSync,
    SWT.INDETERMINATE | SWT.HORIZONTAL
);

GridData progressLayout =
    new GridData(SWT.FILL, SWT.CENTER, true, false);

progressLayout.heightHint = 10;
progress.setLayoutData(progressLayout);

// Dispose custom fonts when the dialog closes.
shlWaiForDevicesSync.addListener(SWT.Dispose, event -> {
    if (!titleFont.isDisposed()) {
        titleFont.dispose();
    }

    if (!statusFont.isDisposed()) {
        statusFont.dispose();
    }
});


    // Preserve the existing close protection.
    shlWaiForDevicesSync.addListener(SWT.Close, new Listener() {
        @Override
        public void handleEvent(Event event) {

            if (canClose) {
                result = "";
                event.doit = true;
            } else {
                WidgetTask.openOKBox(
                    shlWaiForDevicesSync,
                    "Please wait for synchronisation to finish."
                );
                event.doit = false;
            }
        }
    });

    // Existing device synchronisation job.

DevicesSyncJob sync = new DevicesSyncJob("GitSync", message -> {
    if (!display.isDisposed()) {
        display.asyncExec(() -> {
            if (!shlWaiForDevicesSync.isDisposed()) {
                setStatus(message);
            }
        });
    }
});


    sync.addJobChangeListener(new IJobChangeListener() {

        public void aboutToRun(IJobChangeEvent event) {}
        public void awake(IJobChangeEvent event) {}
        public void running(IJobChangeEvent event) {}
        public void scheduled(IJobChangeEvent event) {}
        public void sleeping(IJobChangeEvent event) {}

@Override
public void done(IJobChangeEvent event) {

    if (display.isDisposed()) {
        return;
    }

    display.asyncExec(() -> {

        if (shlWaiForDevicesSync.isDisposed()) {
            return;
        }

        try {
            if (event.getResult() == null ||
                !event.getResult().isOK()) {

                WidgetTask.openOKBox(
                    shlWaiForDevicesSync,
                    "Device synchronisation failed. " +
                    "Flashtool will try locally available definitions."
                );
            }

            if (startupContinuation != null) {
                // Begin the remaining startup operations.
                // They will close the dialog when complete.
                startupContinuation.run();
            } else {
                // Manual synchronisation ends here.
                finishStartup();
            }

        } catch (RuntimeException ex) {
            log.error("Startup continuation failed", ex);
            finishStartup();
        }
    });
}

    });

    sync.schedule();
}

}
