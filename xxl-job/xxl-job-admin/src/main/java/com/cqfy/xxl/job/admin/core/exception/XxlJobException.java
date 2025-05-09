package com.cqfy.xxl.job.admin.core.exception;

/**
 * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/11
 * @Description:自动移的异常类
 */
public class XxlJobException extends RuntimeException {

    public XxlJobException() {
    }
    public XxlJobException(String message) {
        super(message);
    }

}
