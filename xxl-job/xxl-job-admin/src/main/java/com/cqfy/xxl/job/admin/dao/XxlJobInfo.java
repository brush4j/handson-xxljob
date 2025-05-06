package com.cqfy.xxl.job.admin.dao;

import java.util.List;

/**
 * 调度中心负责持久化定时任务信息
 */
public class XxlJobInfo {

    //定时任务主键id
    private int id;

    //定时任务的方法名称
    private String executorHandler;

    //定时任务的下一次执行时间
    private long triggerNextTime;

    //定时任务部署的服务器ip地址的集合
    private List<String> registryList;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getExecutorHandler() {
        return executorHandler;
    }

    public void setExecutorHandler(String executorHandler) {
        this.executorHandler = executorHandler;
    }


    public long getTriggerNextTime() {
        return triggerNextTime;
    }

    public void setTriggerNextTime(long triggerNextTime) {
        this.triggerNextTime = triggerNextTime;
    }

    public List<String> getRegistryList() {
        return registryList;
    }

    public void setRegistryList(List<String> registryList) {
        this.registryList = registryList;
    }

}