package com.cqfy.xxl.job.core.executor.impl;

import com.cqfy.xxl.job.core.executor.XxlJobExecutor;
import com.cqfy.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：jj。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/8/2
 * @Description:不依赖SpringBoot的执行器
 */
public class XxlJobSimpleExecutor extends XxlJobExecutor {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobSimpleExecutor.class);


    private List<Object> xxlJobBeanList = new ArrayList<>();
    public List<Object> getXxlJobBeanList() {
        return xxlJobBeanList;
    }
    public void setXxlJobBeanList(List<Object> xxlJobBeanList) {
        this.xxlJobBeanList = xxlJobBeanList;
    }


    @Override
    public void start() {

        initJobHandlerMethodRepository(xxlJobBeanList);

        try {
            super.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
    }


    private void initJobHandlerMethodRepository(List<Object> xxlJobBeanList) {
        if (xxlJobBeanList==null || xxlJobBeanList.size()==0) {
            return;
        }
        for (Object bean: xxlJobBeanList) {
            // method
            Method[] methods = bean.getClass().getDeclaredMethods();
            if (methods.length == 0) {
                continue;
            }
            for (Method executeMethod : methods) {
                XxlJob xxlJob = executeMethod.getAnnotation(XxlJob.class);
                registJobHandler(xxlJob, bean, executeMethod);
            }
        }
    }
}
