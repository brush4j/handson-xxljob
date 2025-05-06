package com.cqfy.xxl.job.admin.model;

import java.io.Serializable;

public class TriggerParam implements Serializable {
    private static final long serialVersionUID = 42L;

    //定时任务方法的名字
    private String executorHandler;

    public String getExecutorHandler() {
        return executorHandler;
    }

    public void setExecutorHandler(String executorHandler) {
        this.executorHandler = executorHandler;
    }

}
