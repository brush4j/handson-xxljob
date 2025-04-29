package com.cqfy.xxl.job.admin;

import com.cqfy.xxl.job.admin.cron.CronExpression;
import com.cqfy.xxl.job.admin.dao.XxlJobInfo;
import com.cqfy.xxl.job.admin.dao.XxlJobInfoDao;
import com.cqfy.xxl.job.admin.dao.XxlJobInfoDaoImpl;

import java.text.ParseException;
import java.util.Date;
import java.util.List;

public class JobScheduleHelper {
    public static XxlJobInfoDao dao = new XxlJobInfoDaoImpl();
    //定义一个cron表达式，在这里我把执行时间改为了每天的晚上22点
    public static String scheduleCron = "0 0 22 * * ?";
    /**
     * 执行器方业务逻辑
     */
    public static void selfishHeart() {
        System.out.println("执行定时任务！");
    }

    /**
     * 调度中心方：调度定时任务
     * @param args
     */
    public static void main(String[] args) throws ParseException {
        while (true) {
            //从数据库中查询所有定时任务信息
            List<XxlJobInfo> jobInfoList = dao.findAll();
            //得到当前时间
            long time = System.currentTimeMillis();
            //遍历所有定时任务信息
            for (XxlJobInfo jobInfo : jobInfoList) {
                if (time >= jobInfo.getTriggerNextTime()) {
                    //如果大于就执行定时任务，在这里就选用集合的第一个地址
                    String address = jobInfo.getRegistryList().get(0);
                    //注意，既然调度模块已经单独部署了，就没有再创建新的线程去执行定时任务
                    //而是远程通知定时任务程序执行定时任务，没被通知的定时任务程序就不必执行
                    System.out.println("通知address服务器，去执行定时任务!");
                    //计算定时任务下一次的执行时间
                    Date nextTime = new CronExpression(scheduleCron).getNextValidTimeAfter(new Date());
                    //下面就是更新数据库中定时任务的操作
                    XxlJobInfo job = new XxlJobInfo();
                    job.setExecutorHandler("selfishHeart");
                    job.setTriggerNextTime(nextTime.getTime());
                    dao.save(job);
                }
            }
        }
    }
}