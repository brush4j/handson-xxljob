package com.cqfy.xxl.job.admin.core.scheduler;

import com.cqfy.xxl.job.admin.core.thread.JobScheduleHelper;
import com.cqfy.xxl.job.admin.core.thread.JobTriggerPoolHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XxlJobScheduler {

    private static final Logger logger = LoggerFactory.getLogger(XxlJobScheduler.class);

    //初始化调度中心组件的方法
    public void init() throws Exception {
        //在这里，首先把真正远程调度定时任务的线程池创建了
        JobTriggerPoolHelper.toStart();
        //在这里调度定时任务的线程被启动了，然后才可以把能够执行的定时任务
        //的信息提交给线程池
        JobScheduleHelper.getInstance().start();
    }
}