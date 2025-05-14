这一章我们的知识讲解转移到了执行器这一端。一共有三个小知识需要讲解，
- 第一个就是执行器这一端过期日志的清除功能，
- 第二个就是定时任务调度的阻塞策略的体现。
- 第三个是调度中心端发起，终止执行器的工作任务线程。

现在我来为大家依次讲解这些知识点。

## 执行器端过期文件日志清除
首先就是执行器这一端过期日志的清除，当然，和调度中心一样，也要引入一个新的组件，而日志的过期时间同样也是被用户定义在配置文件中，这里就不重复展示了。

直接为大家引入清楚过期日志的组件，也就是JobLogFileCleanThread类。请看下面代码块。
```java
public class JobLogFileCleanThread {

    private static Logger logger = LoggerFactory.getLogger(JobLogFileCleanThread.class);

    //创建单例对象
    private static JobLogFileCleanThread instance = new JobLogFileCleanThread();
    //把对象暴露出去
    public static JobLogFileCleanThread getInstance(){
        return instance;
    }
    //工作线程
    private Thread localThread;
    //判断线程是否停止工作
    private volatile boolean toStop = false;


    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：jj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/8/1
     * @Description:启动该组件的方法
     */
    public void start(final long logRetentionDays){
        //logRetentionDays为用户在配置文件设定的日志过期时间
        //这里有个判断，如果日志过期时间少于3天就直接退出
        if (logRetentionDays < 3 ) {
            return;
        }
        localThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!toStop) {
                    try {
                        //得到该路径下的所有日志文件
                        File[] childDirs = new File(XxlJobFileAppender.getLogPath()).listFiles();
                        if (childDirs!=null && childDirs.length>0) {
                            //判断日志文件数组非空
                            //得到当前时间
                            Calendar todayCal = Calendar.getInstance();
                            //设置日期
                            todayCal.set(Calendar.HOUR_OF_DAY,0);
                            //设置分钟
                            todayCal.set(Calendar.MINUTE,0);
                            //设置秒
                            todayCal.set(Calendar.SECOND,0);
                            //设置毫秒
                            todayCal.set(Calendar.MILLISECOND,0);
                            //得到零点时间
                            Date todayDate = todayCal.getTime();
                            //遍历日志文件
                            for (File childFile: childDirs) {
                                //如果不是文件夹就跳过这次循环，因为现在找到的都是文件夹，文件夹的名称是定时任务执行的年月日时间
                                //比如，2023-06-30，2023-07-02等等，每个时间都是一个文件见，文件夹中有很多个日志文件，文件名称就是定时任务的ID
                                if (!childFile.isDirectory()) {
                                    continue;
                                }
                                //判断文件夹中是否有-符号，根据我上面举的例子，显然有文件夹的名称中有-符号
                                //如果没有则跳过这个文件夹
                                if (childFile.getName().indexOf("-") == -1) {
                                    continue;
                                }
                                //该变量就用来记录日志文件的创建时间，其实就是文件夹的名字
                                Date logFileCreateDate = null;
                                try {
                                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                                    //得到创建时间
                                    logFileCreateDate = simpleDateFormat.parse(childFile.getName());
                                } catch (ParseException e) {
                                    logger.error(e.getMessage(), e);
                                }
                                if (logFileCreateDate == null) {
                                    continue;
                                }
                                //计算刚才得到的今天的零点时间减去日志文件创建的时间是否大于了用户设定的日志过期时间
                                if ((todayDate.getTime()-logFileCreateDate.getTime()) >= logRetentionDays * (24 * 60 * 60 * 1000) ) {
                                    //如果超过了就把过期的日志删除了
                                    FileUtil.deleteRecursively(childFile);
                                }
                            }
                        }
                    } catch (Exception e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }

                    }
                    try {
                        TimeUnit.DAYS.sleep(1);
                    } catch (InterruptedException e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }
                logger.info(">>>>>>>>>>> xxl-job, executor JobLogFileCleanThread thread destroy.");

            }
        });
        localThread.setDaemon(true);
        localThread.setName("xxl-job, executor JobLogFileCleanThread");
        localThread.start();
    }



    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：jj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/8/1
     * @Description:终止组件运行的方法
     */
    public void toStop() {
        toStop = true;
        if (localThread == null) {
            return;
        }
        localThread.interrupt();
        try {
            localThread.join();
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
```

整个类就这么点东西，整体逻辑就是得到创建的所有日志文件夹，日志文件夹的名称就是该文件夹创建的时间，这个知识在第七章已经讲过了。如果当前时间减去文件夹创建的时间大于用户设定的过期时间了，说明该文件夹中存储的日志已经过期了，可以被删除了，然后删除即可。

## 工作任务线程阻塞策略实现
接下来就是定时任务阻塞策略的体现，在看代码之前，先让我来为大家解释一下，阻塞策略的具体含义。

请大家思考这样一个场景，如果有一个定时任务每隔5秒就要执行一次，调度任务调度了定时任务，但是出于某种原因，本次调度的定时任务耗时比较长，可能过了6秒还没有执行完，那么下一次定时任务调度的时候，这个耗时很长的定时任务仍然在执行。

这样一来，后面调度的定时任务就无法执行了，这就是从定时任务执行角度发生的阻塞。如果遇到这种情况，该怎么做呢？

xxl-job中给出了三种阻塞策略的选择，
- 第一就是串行，既然前一个定时任务没有执行完，那么就把现在调度的定时任务的信息存放到定时任务对应的工作线程内部的任务队列中，慢慢等待调度。
- 第二个就是直接丢弃，就是在执行器这一端，如果检测到工作线程内的任务队列中有数据，说明还有被调度的定时任务尚未执行呢，如果是这种情况，而且调度策略还是直接丢弃，那么本次被调度的定时任务就不会被执行，并且立刻返回给调度中心一个调度失败的结果。
- 如果是第三种策略，也就是覆盖，这就意味着当前正在执行的定时任务就不会再被执行了，而是直接开始执行本次调度的定时任务。实际上就是把定时任务对应的工作线程置为null，然后再创建一个新的工作线程，然后直接执行本次调度的定时任务。

具体的实现逻辑就在ExecutorBizImpl类的run方法中，请看下面的枚举类。
```java
/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/12
 * @Description:阻塞处理策略
 */
public enum ExecutorBlockStrategyEnum {

    //串行
    SERIAL_EXECUTION("Serial execution"),

    //直接丢弃
    DISCARD_LATER("Discard Later"),
    //覆盖
    COVER_EARLY("Cover Early");

    private String title;
    private ExecutorBlockStrategyEnum (String title) {
        this.title = title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    public String getTitle() {
        return title;
    }


    public static ExecutorBlockStrategyEnum match(String name, ExecutorBlockStrategyEnum defaultItem) {
        if (name != null) {
            for (ExecutorBlockStrategyEnum item:ExecutorBlockStrategyEnum.values()) {
                if (item.name().equals(name)) {
                    return item;
                }
            }
        }
        return defaultItem;
    }
}
```

接着就是ExecutorBizImpl类。这个类在这一章根据阻塞策略重构了。
```java
/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/8
 * @Description:该类就是在执行器段进行定时任务调用的类
 */
public class ExecutorBizImpl implements ExecutorBiz {

    private static Logger logger = LoggerFactory.getLogger(ExecutorBizImpl.class);


    //其他内容暂时省略



    
    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:执行定时任务的方法，这里要再次强调一下，该方法是在用户定义的业务线程池中调用的
     */
    @Override
    public ReturnT<String> run(TriggerParam triggerParam) {
        //通过定时任务的ID从jobThreadRepository这个Map中获取一个具体的用来执行定时任务的线程
        JobThread jobThread = XxlJobExecutor.loadJobThread(triggerParam.getJobId());
        //判断该jobThread是否为空，不为空则说明该定时任务不是第一次执行了，也就意味着该线程已经分配了定时任务了，也就是这个jobHandler对象
        //如果为空，说明该定时任务是第一次执行，还没有分配jobThread
        IJobHandler jobHandler = jobThread!=null?jobThread.getHandler():null;
        //这个变量记录的是移除旧的工作线程的原因
        String removeOldReason = null;
        //得到定时任务的调度模式
        GlueTypeEnum glueTypeEnum = GlueTypeEnum.match(triggerParam.getGlueType());
        //如果为bean模式，就通过定时任务的名字，从jobHandlerRepository这个Map中获得jobHandler
        if (GlueTypeEnum.BEAN == glueTypeEnum) {
            //在这里获得定时任务对应的jobHandler对象，其实就是MethodJobHandler对象
            IJobHandler newJobHandler = XxlJobExecutor.loadJobHandler(triggerParam.getExecutorHandler());
            //这里会进行一下判断，如果上面得到的jobHandler并不为空，说明该定时任务已经执行过了，并且分配了对应的执行任务的线程
            //但是根据定时任务的名字，从jobHandlerRepository这个Map中得到封装定时任务方法的对象却和jobHandler不相同
            //说明定时任务已经改变了
            if (jobThread!=null && jobHandler != newJobHandler) {
                //走到这里就意味着定时任务已经改变了，要做出相应处理，需要把旧的线程杀死
                removeOldReason = "change jobhandler or glue type, and terminate the old job thread.";
                //执行定时任务的线程和封装定时任务方法的对象都置为null
                jobThread = null;
                jobHandler = null;
            }
            if (jobHandler == null) {
                //如果走到这里，就意味着jobHandler为null，这也就意味着上面得到的jobThread为null
                //这就说明，这次调度的定时任务是第一次执行，所以直接让jobHandler等于从jobHandlerRepository这个Map获得newJobHandler即可
                //然后，这个jobHandler会在下面创建JobThread的时候用到
                jobHandler = newJobHandler;
                if (jobHandler == null) {
                    //经过上面的赋值，
                    //走到这里如果jobHandler仍然为null，那只有一个原因，就是执行器这一端根本就没有对应的定时任务
                    //通过执行器的名字根本从jobHandlerRepository这个Map中找不到要被执行的定时任务
                    return new ReturnT<String>(ReturnT.FAIL_CODE, "job handler [" + triggerParam.getExecutorHandler() + "] not found.");
                }
            }
        }
        else {
            //如果没有合适的调度模式，就返回调用失败的信息
            return new ReturnT<String>(ReturnT.FAIL_CODE, "glueType[" + triggerParam.getGlueType() + "] is not valid.");
        }
        //走到这里只是判断jobThread不为null，说明执行器端已经为该定时任务创建了工作线程了
        if (jobThread != null) {
            //得到该定时任务的阻塞策略
            ExecutorBlockStrategyEnum blockStrategy = ExecutorBlockStrategyEnum.match(triggerParam.getExecutorBlockStrategy(), null);
            if (ExecutorBlockStrategyEnum.DISCARD_LATER == blockStrategy) {
                //走到这里说明定时任务的阻塞策略为直接丢弃
                //所以接下来要判断一下执行该定时任务的线程是否正在工作，如果正在工作并且其内部的队列中有数据
                //说明该线程执行的定时任务已经被调度过几次了，但是还未执行，只能暂时缓存在工作线程的内部队列中
                if (jobThread.isRunningOrHasQueue()) {
                    //因为阻塞策略是直接丢弃，所以直接返回失败结果
                    return new ReturnT<String>(ReturnT.FAIL_CODE, "block strategy effect："+ExecutorBlockStrategyEnum.DISCARD_LATER.getTitle());
                }
                //走到这里说明得到的阻塞策略为覆盖，覆盖的意思就是旧的任务不执行了，直接执行这个新的定时任务
            } else if (ExecutorBlockStrategyEnum.COVER_EARLY == blockStrategy) {
                if (jobThread.isRunningOrHasQueue()) {
                    removeOldReason = "block strategy effect：" + ExecutorBlockStrategyEnum.COVER_EARLY.getTitle();
                    //所以这里把工作线程的引用置为null，这样下面就可以创建一个新的工作线程，然后缓存到Map中
                    //新的工作线程就是直接执行新的定时任务了，默认的阻塞策略就是串行，都放到工作线程内部的队列中，等待被执行
                    jobThread = null;
                }
            } else {
                //这里源码中还未实现，直接空着即可
            }
        }
        if (jobThread == null) {
            //走到这里意味着定时任务是第一次执行，还没有创建对应的执行定时任务的线程，所以，就在这里把对应的线程创建出来，
            //并且缓存到jobThreadRepository这个Map中
            //在这里就用到了上面赋值过的jobHandler
            jobThread = XxlJobExecutor.registJobThread(triggerParam.getJobId(), jobHandler, removeOldReason);
        }
        //如果走到这里，不管上面是什么情况吧，总之jobThread肯定存在了，所以直接把要调度的任务放到这个线程内部的队列中
        //等待线程去调用，返回一个结果
        ReturnT<String> pushResult = jobThread.pushTriggerQueue(triggerParam);
        return pushResult;
    }
}
```

上面就是重构过的run方法，我把代码展示在这里，注释也十分详细，大家其实可以直接打开源码学习了。后面这几篇文章都是起一个引导作用，知识很少的，因为剩下的知识本来就不多。

## 调度中心端发起，终止执行器的工作任务线程。
好了，本章的最后一个知识点，就是终止任务线程功能。

这个操作实际上是从调度中心那边发起的，调度中心想结束某个定时任务的时候，就会在JobLogController类的logKill方法中发起远程调用，终止在执行器这一端执行的定时任务。说是远程调用，实际上仍然是通过http协议发送消息。请看下面代码块。
```java
/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/17
 * @Description:获得日志信息的类，这个对对应的就是调度日志界面
 */
@Controller
@RequestMapping("/joblog")
public class JobLogController {
    private static Logger logger = LoggerFactory.getLogger(JobLogController.class);


    //其他内容暂时省略

    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：jj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/8/1
     * @Description:终止执行器端工作线程的方法
     */
    @RequestMapping("/logKill")
    @ResponseBody
    public ReturnT<String> logKill(int id){
        XxlJobLog log = xxlJobLogDao.load(id);
        XxlJobInfo jobInfo = xxlJobInfoDao.loadById(log.getJobId());
        if (jobInfo==null) {
            return new ReturnT<String>(500, I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }
        if (ReturnT.SUCCESS_CODE != log.getTriggerCode()) {
            return new ReturnT<String>(500, I18nUtil.getString("joblog_kill_log_limit"));
        }
        ReturnT<String> runResult = null;
        try {
            ExecutorBiz executorBiz = XxlJobScheduler.getExecutorBiz(log.getExecutorAddress());
            //在这里发起远程调用
            runResult = executorBiz.kill(new KillParam(jobInfo.getId()));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            runResult = new ReturnT<String>(500, e.getMessage());
        }
        if (ReturnT.SUCCESS_CODE == runResult.getCode()) {
            log.setHandleCode(ReturnT.FAIL_CODE);
            log.setHandleMsg( I18nUtil.getString("joblog_kill_log_byman")+":" + (runResult.getMsg()!=null?runResult.getMsg():""));
            log.setHandleTime(new Date());
            XxlJobCompleter.updateHandleInfoAndFinish(log);
            return new ReturnT<String>(runResult.getMsg());
        } else {
            return new ReturnT<String>(500, runResult.getMsg());
        }
    }

}

```

而在执行器那一端，接收到调度中心发起的远程调用后，就会根据定时任务的ID得到执行该定时任务的工作线程，然后就会将该工作线程从Map中删除，并且终止该线程的运行。请看下面代码块。

该功能也是在执行器端的ExecutorBizImpl类中实现的。
```java
/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/8
 * @Description:该类就是在执行器段进行定时任务调用的类
 */
public class ExecutorBizImpl implements ExecutorBiz {

    private static Logger logger = LoggerFactory.getLogger(ExecutorBizImpl.class);


    //其他内容暂时省略


    
    //终止任务的方法
    @Override
    public ReturnT<String> kill(KillParam killParam) {
        //根据任务ID获取到对应的执行任务的线程
        JobThread jobThread = XxlJobExecutor.loadJobThread(killParam.getJobId());
        if (jobThread != null) {
            //从Map中移除该线程，同时也终止该线程
            XxlJobExecutor.removeJobThread(killParam.getJobId(), "scheduling center kill job.");
            return ReturnT.SUCCESS;
        }
        //返回成功结果
        return new ReturnT<String>(ReturnT.SUCCESS_CODE, "job thread already killed.");
    }

}
```

到此为止，这一章的内容就全部结束了。

## 本节测试

启动admin服务

启动sample服务

会发现执行器地址已经注册到执行器注册表`xxl_job_registry`中了
```sql
id;registry_group;registry_key;registry_value;update_time
1;EXECUTOR;xxl-job-executor-sample;http://:9999/;2025-05-09 14:07:27
5;EXECUTOR;xxl-job-executor-sample;http://10.77.182.251:9999/;2025-05-12 10:49:13
```

本节你可以删除xxl_job_group表中的address_list地址字段值，来验证调度中心能够自动的同步xxl_job_registry执行器的地址并注册到到xxl_job_group

在admin管理页面手动执行一次定时任务，触发的时候用的xxl_job_group中的数据，测试结果在执行器侧打印如下
```shell
第0次
第1次
第2次
第3次
第4次
下一次任务开始了！
```
