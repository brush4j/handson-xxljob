package com.cqfy.xxl.job.core.biz;


import com.cqfy.xxl.job.core.biz.model.RegistryParam;
import com.cqfy.xxl.job.core.biz.model.ReturnT;

/**
 * @author:Halfmoonly
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/11
 * @Description:程序内部使用的接口，该接口是调度中心暴露给执行器那一端的
 */
public interface AdminBiz {


    /**
     * @author:Halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/11
     * @Description:执行器注册自己到调度中心的方法
     */
    public ReturnT<String> registry(RegistryParam registryParam);


    /**
     * @author:Halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/11
     * @Description:执行器将自己从调度中心移除的方法
     */
    public ReturnT<String> registryRemove(RegistryParam registryParam);


}
