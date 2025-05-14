package com.cqfy.xxl.job.core.handler.impl;


import com.cqfy.xxl.job.core.handler.IJobHandler;

import java.lang.reflect.Method;

/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/8
 * @Description:该类的作用就是反射调用定时任务的
 */
public class MethodJobHandler extends IJobHandler {

    //目标类对象，就是用户定义的IOC容器中的bean
    private final Object target;
    //目标方法，就是要被执行的定时任务方法
    private final Method method;
    //bean对象的初始化方法
    private Method initMethod;
    //bean对象的销毁方法
    private Method destroyMethod;

    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:构造方法
     */
    public MethodJobHandler(Object target, Method method, Method initMethod, Method destroyMethod) {
        this.target = target;
        this.method = method;
        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;
    }


    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:通过反射执行定时任务方法
     */
    @Override
    public void execute() throws Exception {
        //获取当前定时任务方法的参数类型合集
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 0) {
            //反射调用方法
            method.invoke(target, new Object[paramTypes.length]);
        } else {
            //没有参数，就直接反射调用方法
            method.invoke(target);
        }
    }

    //反射调用目标对象的init方法
    @Override
    public void init() throws Exception {
        if(initMethod != null) {
            initMethod.invoke(target);
        }
    }

    //反射调用目标对象的destroy方法
    @Override
    public void destroy() throws Exception {
        if(destroyMethod != null) {
            destroyMethod.invoke(target);
        }
    }

    @Override
    public String toString() {
        return super.toString()+"["+ target.getClass() + "#" + method.getName() +"]";
    }
}
