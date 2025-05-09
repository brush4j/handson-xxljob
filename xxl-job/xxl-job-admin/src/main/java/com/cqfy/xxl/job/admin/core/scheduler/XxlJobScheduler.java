package com.cqfy.xxl.job.admin.core.scheduler;

import com.cqfy.xxl.job.admin.core.thread.JobRegistryHelper;
import com.cqfy.xxl.job.admin.core.thread.JobScheduleHelper;
import com.cqfy.xxl.job.admin.core.thread.JobTriggerPoolHelper;
import com.cqfy.xxl.job.core.thread.ExecutorRegistryThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XxlJobScheduler {

    private static final Logger logger = LoggerFactory.getLogger(XxlJobScheduler.class);

    //初始化调度中心组件的方法
    public void init() throws Exception {
        //在这里，首先把真正远程调度定时任务的线程池创建了
        JobTriggerPoolHelper.toStart();
        //初始化注册中心组件，这里是个简易版本，后面会重构到和源码一致
        //现在的版本并没有定时清理过期服务实例的功能
        JobRegistryHelper.getInstance().start();
        //初始化任务调度线程，这个线程可以说是xxl-job服务端的核心了
        //注意，大家在理解任务调度的时候，没必要把这个概念搞得特别复杂，所谓调度，就是哪个任务该执行了
        //这个线程就会把该任务提交了去执行，这就是调度的含义，这个线程会一直扫描判断哪些任务应该执行了
        //这里面会用到时间轮。这里我要再次强调一下，时间轮并不是线程，时间轮本身需要一个配合线程工作的容器
        //如果学过我的从零带你学Netty这门课，就会明白，时间轮的容器，可以用数组实现，也可以用Map实现
        //说得更准确点，容器加上工作线程组成了时间轮
        JobScheduleHelper.getInstance().start();
    }
    /**
     * @author:halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/2
     * @Description:释放资源的方法
     */
    public void destroy() throws Exception {

        JobScheduleHelper.getInstance().toStop();

        JobTriggerPoolHelper.toStop();

    }
}