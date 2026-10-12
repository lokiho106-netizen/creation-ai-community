package com.creationai.community.automation;

import android.content.Context;
import android.content.SharedPreferences;

public final class WorkflowStatusStore {
    private static final String FILE = "workflow_status";
    private static final String STATUS = "status";
    private static final String STATE = "state";

    private final SharedPreferences prefs;

    public WorkflowStatusStore(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public void write(WorkflowState state, String status) {
        prefs.edit()
                .putString(STATE, state.name())
                .putString(STATUS, status == null ? "" : status)
                .apply();
    }

    public String readDisplayText() {
        return prefs.getString(STATE, WorkflowState.IDLE.name()) + " · "
                + prefs.getString(STATUS, "未运行");
    }
}
