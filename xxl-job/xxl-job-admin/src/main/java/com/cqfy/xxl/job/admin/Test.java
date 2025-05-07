package com.cqfy.xxl.job.admin;

import com.cqfy.xxl.job.admin.core.scheduler.XxlJobScheduler;

public class Test {

    public static void main(String[] args) throws Exception {
        //创建调度中心
        XxlJobScheduler xxlJobScheduler = new XxlJobScheduler();
        //初始化调度中心组件，到此为止，我重构的调度中心已经有两个组件了
        //就是JobTriggerPoolHelper和JobScheduleHelper
        xxlJobScheduler.init();
    }
}