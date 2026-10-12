package com.creationai.community.automation;

import android.os.SystemClock;

public final class WorkflowController {
    private WorkflowState state = WorkflowState.IDLE;
    private long stateStartedAt = SystemClock.elapsedRealtime();
    private String message = "未运行";

    public synchronized void moveTo(WorkflowState next, String reason) {
        state = next;
        stateStartedAt = SystemClock.elapsedRealtime();
        message = reason == null ? "" : reason;
    }

    public synchronized WorkflowState getState() {
        return state;
    }

    public synchronized String getMessage() {
        return message;
    }

    public synchronized boolean timedOut(long timeoutMillis) {
        return SystemClock.elapsedRealtime() - stateStartedAt > timeoutMillis;
    }

    public synchronized void stop(String reason) {
        moveTo(WorkflowState.PAUSED, reason);
    }
}
