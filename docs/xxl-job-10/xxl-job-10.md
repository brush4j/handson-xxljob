在xxl-job中，每一个被调度的定时任务都可以被用户设置失败重试次数，如果一个定时任务的失败重试次数为2，那么当这个定时任务被调度失败的时候，会进行一次重试，并且把可重试次数减1，如果重试之后仍然失败，那么会再次进行一次调度。在调度的时候可重试次数就会减为0。

如果第二次重试仍然失败，那么该定时任务就不会再继续重试了。当然，调度中心调度的定时任务出现执行失败的情况后，也会通过邮件报警的方式，通知给用户。既然是邮件报警，那么邮件的地址就需要用户自己定义在配置文件中。就像下面这样。
```shell### xxl-job, email
spring.mail.host=smtp.qq.com
spring.mail.port=25
spring.mail.username=xxx@qq.com
spring.mail.from=xxx@qq.com
spring.mail.password=xxx
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.properties.mail.smtp.socketFactory.class=javax.net.ssl.SSLSocketFactory
```

## 告警组件定义
而这些信息定义好之后，调度中心当然还要引入邮件报警的组件，才能真正实现报警的功能。具体要引入的类就是下面几个。请看下面的代码块。
```java
/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：jj。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/31
 * @Description:这个类是用来发送报警邮件的，但实际上真正的功能并不在这个类实现，而是在EmailJobAlarm类实现
 */
@Component
public class JobAlarmer implements ApplicationContextAware, InitializingBean {

    private static Logger logger = LoggerFactory.getLogger(JobAlarmer.class);
    //Springboot容器
    private ApplicationContext applicationContext;
    //邮件报警器的集合
    private List<JobAlarm> jobAlarmList;



    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：jj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/31
     * @Description:该方法会在容器中的bean初始化完毕后被回调
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        //把容器中所有的邮件报警器收集到jobAlarmList集合中
        Map<String, JobAlarm> serviceBeanMap = applicationContext.getBeansOfType(JobAlarm.class);
        if (serviceBeanMap != null && serviceBeanMap.size() > 0) {
            jobAlarmList = new ArrayList<JobAlarm>(serviceBeanMap.values());
        }
    }


    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：jj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/31
     * @Description:在JobFailMonitorHelper类中被调用到的发送报警邮件的方法
     */
    public boolean alarm(XxlJobInfo info, XxlJobLog jobLog) {
        boolean result = false;
        //先判断邮件报警器集合是否为空
        if (jobAlarmList!=null && jobAlarmList.size()>0) {
            //不为空就先设置所有报警器发送结果都为成功
            result = true;
            for (JobAlarm alarm: jobAlarmList) {
                //遍历邮件报警器，然后设置发送结果为false
                boolean resultItem = false;
                try {
                    //在这里真正发送报警邮件给用户，然后返回给用户发送结果
                    resultItem = alarm.doAlarm(info, jobLog);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
                if (!resultItem) {
                    //这里可以看到，如果发送失败，就把最开始设置的result重新改为false
                    //并且这里可以明白，只要有一个报警器发送邮件失败，总的发送结果就会被设置为失败
                    result = false;
                }
            }
        }
        return result;
    }
}

```

接下来就是具体的发送邮件警报的EmailJobAlarm类。
```java
/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：jj。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/31
 * @Description:发送报警邮件的类
 */
@Component
public class EmailJobAlarm implements JobAlarm {

    private static Logger logger = LoggerFactory.getLogger(EmailJobAlarm.class);


    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：jj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/31
     * @Description:真正发送报警邮件的逻辑
     */
    @Override
    public boolean doAlarm(XxlJobInfo info, XxlJobLog jobLog){
        boolean alarmResult = true;
        //做一些判空校验
        if (info!=null && info.getAlarmEmail()!=null && info.getAlarmEmail().trim().length()>0) {
            //得到报警的定时任务的id
            String alarmContent = "Alarm Job LogId=" + jobLog.getId();
            //下面注释我就不详细写了，都很简单，都是结果内容的设置，大家应该都对这些很熟悉了
            if (jobLog.getTriggerCode() != ReturnT.SUCCESS_CODE) {
                alarmContent += "<br>TriggerMsg=<br>" + jobLog.getTriggerMsg();
            }
            if (jobLog.getHandleCode()>0 && jobLog.getHandleCode() != ReturnT.SUCCESS_CODE) {
                alarmContent += "<br>HandleCode=" + jobLog.getHandleMsg();
            }
            //得到执行器组
            XxlJobGroup group = XxlJobAdminConfig.getAdminConfig().getXxlJobGroupDao().load(Integer.valueOf(info.getJobGroup()));
            //设置报警信息的发送者，具体值大家可以去I18nUtil的配置文件中查看
            String personal = I18nUtil.getString("admin_name_full");
            //设置报警信息的标题
            String title = I18nUtil.getString("jobconf_monitor");
            //向模版中填充具体的内容，就不具体解释了，都很简单
            String content = MessageFormat.format(loadEmailJobAlarmTemplate(),
                    group!=null?group.getTitle():"null",
                    info.getId(),
                    info.getJobDesc(),
                    alarmContent);
            //也许设置了多个邮件地址，所以这里把它转化为集合
            Set<String> emailSet = new HashSet<String>(Arrays.asList(info.getAlarmEmail().split(",")));
            //遍历地址，然后就是给每一个地址发送报警邮件了
            for (String email: emailSet) {
                try {
                    //下面这些步骤就不用详细解释了吧，这些都是常规流程了，用过mail的jar包都很熟悉了吧
                    MimeMessage mimeMessage = XxlJobAdminConfig.getAdminConfig().getMailSender().createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
                    helper.setFrom(XxlJobAdminConfig.getAdminConfig().getEmailFrom(), personal);
                    helper.setTo(email);
                    helper.setSubject(title);
                    helper.setText(content, true);
                    XxlJobAdminConfig.getAdminConfig().getMailSender().send(mimeMessage);
                } catch (Exception e) {
                    logger.error(">>>>>>>>>>> xxl-job, job fail alarm email send error, JobLogId:{}", jobLog.getId(), e);
                    alarmResult = false;
                }
            }
        }
        //返回发送结果
        return alarmResult;
    }



    /**
     * load email job alarm template
     *  这个是前端要用到的模版
     * @return
     */
    private static final String loadEmailJobAlarmTemplate(){
        String mailBodyTemplate = "<h5>" + I18nUtil.getString("jobconf_monitor_detail") + "：</span>" +
                "<table border=\"1\" cellpadding=\"3\" style=\"border-collapse:collapse; width:80%;\" >\n" +
                "   <thead style=\"font-weight: bold;color: #ffffff;background-color: #ff8c00;\" >" +
                "      <tr>\n" +
                "         <td width=\"20%\" >"+ I18nUtil.getString("jobinfo_field_jobgroup") +"</td>\n" +
                "         <td width=\"10%\" >"+ I18nUtil.getString("jobinfo_field_id") +"</td>\n" +
                "         <td width=\"20%\" >"+ I18nUtil.getString("jobinfo_field_jobdesc") +"</td>\n" +
                "         <td width=\"10%\" >"+ I18nUtil.getString("jobconf_monitor_alarm_title") +"</td>\n" +
                "         <td width=\"40%\" >"+ I18nUtil.getString("jobconf_monitor_alarm_content") +"</td>\n" +
                "      </tr>\n" +
                "   </thead>\n" +
                "   <tbody>\n" +
                "      <tr>\n" +
                "         <td>{0}</td>\n" +
                "         <td>{1}</td>\n" +
                "         <td>{2}</td>\n" +
                "         <td>"+ I18nUtil.getString("jobconf_monitor_alarm_type") +"</td>\n" +
                "         <td>{3}</td>\n" +
                "      </tr>\n" +
                "   </tbody>\n" +
                "</table>";
        return mailBodyTemplate;
    }

}

```

## 告警监控线程JobFailMonitorHelper
上面的邮件报警非常简单，而且我相信大家应该也都使用过，所以，就下来就让我们尽快看看这一章的核心功能，也就是JobFailMonitorHelper类的具体内容是什么吧。请看下面的代码块。
```java
public class JobFailMonitorHelper {

    private static Logger logger = LoggerFactory.getLogger(JobFailMonitorHelper.class);
    //创建单例对象
    private static JobFailMonitorHelper instance = new JobFailMonitorHelper();
    //把对象暴露出去
    public static JobFailMonitorHelper getInstance(){
        return instance;
    }


    //处理失败任务告警的线程
    private Thread monitorThread;
    //线程是否停止工作
    private volatile boolean toStop = false;



    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：jj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/31
     * @Description:启动该组件的方法
     */
    public void start(){
        monitorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!toStop) {
                    try {
                        //从数据库中查询执行失败的任务，查询的数量为1000，这里我把findFailJobLogIds方法底层对应的sql语句给大家展示出来
                        //<select id="findFailJobLogIds" resultType="long" >
                        //		SELECT id FROM `xxl_job_log`
                        //		WHERE !(
                        //			(trigger_code in (0, 200) and handle_code = 0)
                        //			OR
                        //			(handle_code = 200)
                        //		)
                        //		AND `alarm_status` = 0
                        //		ORDER BY id ASC
                        //		LIMIT #{pagesize}
                        //	</select>
                        //对应的就是这个语句，请大家注意，这里查出来的都是执行失败并且报警状态码还未改变的定时任务
                        List<Long> failLogIds = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().findFailJobLogIds(1000);
                        //如果结果不为空，说明存在执行失败的定时任务，并且报警状态码还未改变
                        if (failLogIds!=null && !failLogIds.isEmpty()) {
                            //遍历该集合
                            for (long failLogId: failLogIds) {
                                //updateAlarmStatus方法对应的sql语句
                                //	<update id="updateAlarmStatus" >
                                //		UPDATE xxl_job_log
                                //		SET
                                //			`alarm_status` = #{newAlarmStatus}
                                //		WHERE `id`= #{logId} AND `alarm_status` = #{oldAlarmStatus}
                                //	</update>
                                //在这里把XxlJobLog的alarmStatus修改为-1，-1就是锁定状态，这里大家其实就可以把这个-1看成乐观锁
                                //这一条是源码中的注释，我搬运到这里了。告警状态：0-默认、-1=锁定状态、1-无需告警、2-告警成功、3-告警失败
                                int lockRet = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateAlarmStatus(failLogId, 0, -1);
                                if (lockRet < 1) {
                                    //走到这里说明更新数据库失败
                                    continue;
                                }
                                //这里其实就是根据XxlJobLog的主键ID获得对应的XxlJobLog
                                XxlJobLog log = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().load(failLogId);
                                //根据定时任务ID得到具体的定时任务信息，当然，得到的都是执行失败的定时任务的具体信息
                                XxlJobInfo info = XxlJobAdminConfig.getAdminConfig().getXxlJobInfoDao().loadById(log.getJobId());
                                //判断该定时任务的失败重试次数是否大于0
                                if (log.getExecutorFailRetryCount() > 0) {
                                    //如果大于0，就立刻远程调度一次， (log.getExecutorFailRetryCount()-1这行代码，就会在每次重试的时候把重试次数减1，直到为0
                                    JobTriggerPoolHelper.trigger(log.getJobId(), TriggerTypeEnum.RETRY, (log.getExecutorFailRetryCount()-1), log.getExecutorShardingParam(), log.getExecutorParam(), null);
                                    //记录下来失败重试调用了一次
                                    String retryMsg = "<br><br><span style=\"color:#F39C12;\" > >>>>>>>>>>>"+ I18nUtil.getString("jobconf_trigger_type_retry") +"<<<<<<<<<<< </span><br>";
                                    log.setTriggerMsg(log.getTriggerMsg() + retryMsg);
                                    //跟新数据库的信息，就是把XxlJobLog更新一下，因为这个定时任务的日志中记录了失败重试的信息
                                    XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateTriggerInfo(log);
                                }
                                //定义一个新的报警状态
                                int newAlarmStatus = 0;
                                if (info != null) {
                                    //如果查询到执行失败的定时任务了，就直接报警，发送告警邮件
                                    boolean alarmResult = XxlJobAdminConfig.getAdminConfig().getJobAlarmer().alarm(info, log);
                                    //判断是否发送成功，如果发送成功就把报警状态设置为2，2就代表报警成功了，3就代表失败
                                    newAlarmStatus = alarmResult?2:3;
                                } else {
                                    //如果没有得到对应的XxlJobInfo，就无须报警
                                    newAlarmStatus = 1;
                                }
                                //在这里把最新的状态吗更新到数据库，-1这个值也就不再使用了
                                XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateAlarmStatus(failLogId, -1, newAlarmStatus);
                            }
                        }
                    } catch (Exception e) {
                        if (!toStop) {
                            logger.error(">>>>>>>>>>> xxl-job, job fail monitor thread error:{}", e);
                        }
                    }
                    try {
                        TimeUnit.SECONDS.sleep(10);
                    } catch (Exception e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }
                logger.info(">>>>>>>>>>> xxl-job, job fail monitor thread stop");
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.setName("xxl-job, admin JobFailMonitorHelper");
        monitorThread.start();
    }



    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：jj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/31
     * @Description:终止组件的方法
     */
    public void toStop(){
        toStop = true;
        monitorThread.interrupt();
        try {
            monitorThread.join();
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
```

从上面代码块中可以看出，其实JobFailMonitorHelper类的功能也很简单，其实就是先从数据库查询出所有执行失败的定时任务，然后再遍历这些定时任务，根据每一个定时任务的重试次数进行重试，也就是重新调度。

当然，也会把给用户发送邮件，通知用户哪些定时任务执行失败了。逻辑都写在代码块中了，简单详细，就随便看看吧，或者直接去撸源码。已经没什么知识可讲了。。真的。。