package com.cqfy.xxl.job.admin.core.complete;

import com.cqfy.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.cqfy.xxl.job.admin.core.model.XxlJobInfo;
import com.cqfy.xxl.job.admin.core.model.XxlJobLog;
import com.cqfy.xxl.job.admin.core.thread.JobTriggerPoolHelper;
import com.cqfy.xxl.job.admin.core.trigger.TriggerTypeEnum;
import com.cqfy.xxl.job.admin.core.util.I18nUtil;
import com.cqfy.xxl.job.core.biz.model.ReturnT;
import com.cqfy.xxl.job.core.context.XxlJobContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;



/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/17
 * @Description:更新日志信息，触发子任务的类
 */
public class XxlJobCompleter {

    private static Logger logger = LoggerFactory.getLogger(XxlJobCompleter.class);


    public static int updateHandleInfoAndFinish(XxlJobLog xxlJobLog) {
        //触发子任务的方法
        finishJob(xxlJobLog);
        //判断字符串长度
        if (xxlJobLog.getHandleMsg().length() > 15000) {
            //太长的话需要截取一段
            xxlJobLog.setHandleMsg( xxlJobLog.getHandleMsg().substring(0, 15000) );
        }
        //更新数据库
        return XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateHandleInfo(xxlJobLog);
    }


   /**
    * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
    * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
    * @Date:2023/7/17
    * @Description:触发子任务的方法
    */
    private static void finishJob(XxlJobLog xxlJobLog){
        String triggerChildMsg = null;
        //先判断定时任务是不是执行成功的状态
        if (XxlJobContext.HANDLE_CODE_SUCCESS == xxlJobLog.getHandleCode()) {
            //如果成功了，就先得到该定时任务的具体信息
            XxlJobInfo xxlJobInfo = XxlJobAdminConfig.getAdminConfig().getXxlJobInfoDao().loadById(xxlJobLog.getJobId());
            //判断子任务id不为null
            if (xxlJobInfo!=null && xxlJobInfo.getChildJobId()!=null && xxlJobInfo.getChildJobId().trim().length()>0) {
                triggerChildMsg = "<br><br><span style=\"color:#00c0ef;\" > >>>>>>>>>>>"+ I18nUtil.getString("jobconf_trigger_child_run") +"<<<<<<<<<<< </span><br>";
                //如果有多个子任务，就切分子任务id为数组
                String[] childJobIds = xxlJobInfo.getChildJobId().split(",");
                //遍历子任务id数组
                for (int i = 0; i < childJobIds.length; i++) {
                    //得到子任务id
                    int childJobId = (childJobIds[i]!=null && childJobIds[i].trim().length()>0 && isNumeric(childJobIds[i]))?Integer.valueOf(childJobIds[i]):-1;
                    if (childJobId > 0) {
                        //在这里直接调度子任务
                        JobTriggerPoolHelper.trigger(childJobId, TriggerTypeEnum.PARENT, -1, null, null, null);
                        //设置调度成功的结果
                        ReturnT<String> triggerChildResult = ReturnT.SUCCESS;
                        triggerChildMsg += MessageFormat.format(I18nUtil.getString("jobconf_callback_child_msg1"),
                                (i+1),
                                childJobIds.length,
                                childJobIds[i],
                                (triggerChildResult.getCode()==ReturnT.SUCCESS_CODE?I18nUtil.getString("system_success"):I18nUtil.getString("system_fail")),
                                triggerChildResult.getMsg());
                    } else {
                        triggerChildMsg += MessageFormat.format(I18nUtil.getString("jobconf_callback_child_msg2"),
                                (i+1),
                                childJobIds.length,
                                childJobIds[i]);
                    }
                }
            }
        }
        if (triggerChildMsg != null) {
            xxlJobLog.setHandleMsg( xxlJobLog.getHandleMsg() + triggerChildMsg );
        }
    }



    private static boolean isNumeric(String str){
        try {
            int result = Integer.valueOf(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

}
