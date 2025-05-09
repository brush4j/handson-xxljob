//package com.cqfy.xxl.job.admin.service.impl;
//
//import com.cqfy.xxl.job.admin.core.thread.JobRegistryHelper;
//import com.cqfy.xxl.job.core.biz.AdminBiz;
//import com.cqfy.xxl.job.core.biz.model.RegistryParam;
//import com.cqfy.xxl.job.core.biz.model.ReturnT;
//import org.springframework.stereotype.Service;
//
///**
// * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
// * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
// * @Date:2023/7/4
// * @Description:这个实现类是调度中心要使用的
// */
//@Service
//public class AdminBizImpl implements AdminBiz {
//
//    /**
//     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
//     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
//     * @Date:2023/7/4
//     * @Description:把执行器注册到注册中心
//     */
//    @Override
//    public ReturnT<String> registry(RegistryParam registryParam) {
//        //通过JobRegistryHelper组件中创建的线程池来完成注册任务
//        return JobRegistryHelper.getInstance().registry(registryParam);
//    }
//
//    /**
//     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
//     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
//     * @Date:2023/7/4
//     * @Description:移除执行器的方法
//     */
//    @Override
//    public ReturnT<String> registryRemove(RegistryParam registryParam) {
//        return JobRegistryHelper.getInstance().registryRemove(registryParam);
//    }
//
//}
