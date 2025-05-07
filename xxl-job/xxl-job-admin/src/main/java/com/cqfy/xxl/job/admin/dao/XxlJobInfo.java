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

    //定时任务的最新执行过的时间
    private long triggerLastTime;

    //定义一个cron表达式，如"0 0 22 * * ?";
    private String scheduleConf;

    //调度类型
    private String scheduleType;

    //定时任务触发状态，0为停止，1为运行
    private int triggerStatus;

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
    public String getScheduleConf() {
        return scheduleConf;
    }

    public void setScheduleConf(String scheduleConf) {
        this.scheduleConf = scheduleConf;
    }
    public long getTriggerLastTime() {
        return triggerLastTime;
    }

    public void setTriggerLastTime(long triggerLastTime) {
        this.triggerLastTime = triggerLastTime;
    }
    public String getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(String scheduleType) {
        this.scheduleType = scheduleType;
    }
    public int getTriggerStatus() {
        return triggerStatus;
    }

    public void setTriggerStatus(int triggerStatus) {
        this.triggerStatus = triggerStatus;
    }

}