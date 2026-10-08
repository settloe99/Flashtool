package org.flashtool.gui.tools;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.flashtool.gui.TARestore;
import org.flashtool.jna.adb.FastbootUtility;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SearchFastbootJob extends Job {

	private volatile boolean canceled = false;

	public SearchFastbootJob(String name) {
		super(name);
	}

	public void stopSearch() {
		canceled=true;
		cancel();
	}
	
    @Override
    protected IStatus run(IProgressMonitor monitor) {
        while (!canceled && !monitor.isCanceled()) {

            if (FastbootUtility.getDevices().hasMoreElements()) {
                return (canceled || monitor.isCanceled())
                        ? Status.CANCEL_STATUS
                        : Status.OK_STATUS;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Status.CANCEL_STATUS;
            }
        }

        return Status.CANCEL_STATUS;
    }
}
