package com.cqfy.xxl.job.core.handler;
/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/8
 * @Description:封装定时任务方法的接口
 */
public abstract class IJobHandler {


	public abstract void execute() throws Exception;


	public void init() throws Exception {

	}


	public void destroy() throws Exception {

	}


}
