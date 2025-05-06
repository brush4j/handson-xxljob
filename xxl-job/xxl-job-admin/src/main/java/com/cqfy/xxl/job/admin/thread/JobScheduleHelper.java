package com.cqfy.xxl.job.admin.thread;

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
    //调度定时任务的线程
    private Thread scheduleThread;

    //创建当前类的对象
    private static JobScheduleHelper instance = new JobScheduleHelper();

    //把当前类的对象暴露出去
    public static JobScheduleHelper getInstance(){
        return instance;
    }
    /**
     * 执行器方业务逻辑
     */
    public static void selfishHeart() {
        System.out.println("执行定时任务！");
    }

    /**
     * 调度中心方：调度定时任务
     */
    //启动调度线程工作的方法
    public void start(){

        scheduleThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    //从数据库中查询所有定时任务信息
                    List<XxlJobInfo> jobInfoList = dao.findAll();
                    //得到当前时间
                    long time = System.currentTimeMillis();
                    //遍历所有定时任务信息
                    for (XxlJobInfo jobInfo : jobInfoList) {
                        if (time >= jobInfo.getTriggerNextTime()) {
                            //如果大于就执行定时任务，就调用下面这个方法，开始远程通知定时任务程序
                            //执行定时任务
                            JobTriggerPoolHelper.trigger(jobInfo);
                            //计算定时任务下一次的执行时间
                            Date nextTime = null;
                            try {
                                nextTime = new CronExpression(scheduleCron).getNextValidTimeAfter(new Date());
                            } catch (ParseException e) {
                                throw new RuntimeException("CronExpression解析失败");
                            }
                            //下面就是更新数据库中定时任务的操作
                            XxlJobInfo job = new XxlJobInfo();
                            job.setExecutorHandler("selfishHeart");
                            job.setTriggerNextTime(nextTime.getTime());
                            dao.save(job);
                        }
                    }
                }
            }
        });
        //启动调度线程
        scheduleThread.start();
    }
}