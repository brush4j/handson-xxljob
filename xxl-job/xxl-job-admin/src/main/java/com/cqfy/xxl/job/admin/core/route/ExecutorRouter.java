package com.cqfy.xxl.job.admin.core.route;

import com.cqfy.xxl.job.core.biz.model.ReturnT;
import com.cqfy.xxl.job.core.biz.model.TriggerParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author:Halfmoonly
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/3
 * @Description:路由策略的抽象类，现在还用不到路由策略，所以我们暂且只引入这一个抽象类，只是为了代码不报错
 */
public abstract class ExecutorRouter {
    protected static Logger logger = LoggerFactory.getLogger(ExecutorRouter.class);

    public abstract ReturnT<String> route(TriggerParam triggerParam, List<String> addressList);

}
