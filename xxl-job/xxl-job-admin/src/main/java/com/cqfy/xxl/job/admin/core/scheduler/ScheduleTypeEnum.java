package com.cqfy.xxl.job.admin.core.scheduler;


import com.cqfy.xxl.job.admin.core.util.I18nUtil;

/**
 * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/11
 * @Description:定时任务的调度类型
 */
public enum ScheduleTypeEnum {

    //不使用任何类型
    NONE(I18nUtil.getString("schedule_type_none")),

    //一般都是用cron表达式
    CRON(I18nUtil.getString("schedule_type_cron")),

    //按照固定频率
    FIX_RATE(I18nUtil.getString("schedule_type_fix_rate"));


    private String title;

    ScheduleTypeEnum(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public static ScheduleTypeEnum match(String name, ScheduleTypeEnum defaultItem){
        for (ScheduleTypeEnum item: ScheduleTypeEnum.values()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return defaultItem;
    }

}
