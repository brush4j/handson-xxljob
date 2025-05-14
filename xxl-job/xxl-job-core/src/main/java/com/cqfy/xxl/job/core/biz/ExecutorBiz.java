package com.cqfy.xxl.job.core.biz;


import com.cqfy.xxl.job.core.biz.model.*;

/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/4
 * @Description:用于远程调用的客户端接口，该接口中定义了多个方法，第一版本只保留一个
 */
public interface ExecutorBiz {


    public ReturnT<String> beat();


    public ReturnT<String> idleBeat(IdleBeatParam idleBeatParam);

    //远程调用的方法
    ReturnT<String> run(TriggerParam triggerParam);

    public ReturnT<LogResult> log(LogParam logParam);

}
