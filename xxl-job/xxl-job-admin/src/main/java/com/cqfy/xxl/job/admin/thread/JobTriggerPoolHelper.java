package com.cqfy.xxl.job.admin.thread;

import com.cqfy.xxl.job.admin.dao.XxlJobInfo;
import com.cqfy.xxl.job.admin.trigger.XxlJobTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class JobTriggerPoolHelper {

    private static Logger logger = LoggerFactory.getLogger(JobTriggerPoolHelper.class);

    //定义的快线程池
    private ThreadPoolExecutor fastTriggerPool = null;
    //定义的慢线程池
    private ThreadPoolExecutor slowTriggerPool = null;

    //创建该类的对象
    private static JobTriggerPoolHelper helper = new JobTriggerPoolHelper();

    //对外暴露的该类线程池的方法
    public static void toStart() {
        helper.start();
    }

    //对外暴露终止该类线程池的方法
    public static void toStop() {
        helper.stop();
    }

    public void start() {
        //快线程池，最大线程数为200
        fastTriggerPool = new ThreadPoolExecutor(
                10,
                200,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(1000),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "xxl-job, admin JobTriggerPoolHelper-fastTriggerPool-" + r.hashCode());
                    }
                });

        //慢线程池，最大线程数为100
        slowTriggerPool = new ThreadPoolExecutor(
                10,
                100,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(2000),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "xxl-job, admin JobTriggerPoolHelper-slowTriggerPool-" + r.hashCode());
                    }
                });
    }


    //终止线程池的方法
    public void stop() {
        fastTriggerPool.shutdownNow();
        slowTriggerPool.shutdownNow();
    }

    //该方法并没什变动
    public static void trigger(XxlJobInfo jobInfo) {
        helper.addTrigger(jobInfo);
    }


    //获取当前的系统时间，这里计算出来的其实是系统当前的分钟数，下面马上就会用到
    private volatile long minTim = System.currentTimeMillis() / 60000;
    //如果有任务出现慢执行情况了，就会被记录在该Map中
    //所谓慢执行，就是执行的时间超过了500毫秒，该Map的key为job的id，value为慢执行的次数
    //如果一分钟慢执行的次数超过了10次，该任务就会被交给慢线程池的来执行
    //而该Map也会一分钟清空一次，来循环记录慢执行的情况
    private volatile ConcurrentMap<Integer, AtomicInteger> jobTimeoutCountMap = new ConcurrentHashMap<>();

    //该方法经过重构了，在这里把定时任务信息提交给线程池去远程发送
    public void addTrigger(XxlJobInfo jobInfo) {
        int jobId = jobInfo.getId();
        //默认先选用快线程池
        ThreadPoolExecutor triggerPool_ = fastTriggerPool;
        //用任务Id从，慢执行的Map中得到该job对应的慢执行次数
        AtomicInteger jobTimeoutCount = jobTimeoutCountMap.get(jobId);
        //这里就是具体判断了，如果慢执行次数不为null，并且一分钟超过10了，就选用慢线程池来执行该任务
        if (jobTimeoutCount != null && jobTimeoutCount.get() > 10) {
            //选用慢线程池了
            triggerPool_ = slowTriggerPool;
        }
        //在这里就把任务提交给线程池了，在这个任务执行一个触发器任务，把刚才传进来的job的各种信息整合到一起
        //在触发器任务中，会进行job的远程调用，这个调用链还是比较短的，执行流程也很清晰
        triggerPool_.execute(new Runnable() {
            @Override
            public void run() {
                //再次获取当前时间，这个时间后面会用到
                long start = System.currentTimeMillis();
                try {
                    //这里就是线程池中的线程去执行远程调度定时任务的任务了
                    XxlJobTrigger.trigger(jobInfo);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                } finally {
                    //这里再次获得当前的分钟数，这个分钟数会和刚才上面得到的那个分钟数做对比
                    long minTim_now = System.currentTimeMillis() / 60000;
                    //这里就用到了两个分钟数做对比，如果两个分钟数不等，说明过去了一分钟
                    //而慢执行Map中的数据是一分钟清理一次，所以这里就把慢执行Map清空
                    //注意，这个清空的动作是线程池中的线程来执行的，并且这个动作是在finally代码块中执行的
                    //也就意味着是在上面的触发器任务执行完毕后才进行清空操作
                    if (minTim != minTim_now) {
                        minTim = minTim_now;
                        jobTimeoutCountMap.clear();
                    }
                    //在这里用当前毫秒值减去之前得到的毫秒值
                    long cost = System.currentTimeMillis() - start;
                    //判断任务的执行时间是否超过500毫秒了
                    //这里仍然要结合上面的finally代码块来理解，因为触发器任务执行完了才会执行finally代码块中的
                    //代码，所以这时候也就能得到job的执行时间了
                    if (cost > 500) {
                        //超过500毫秒了，就判断当前执行的任务为慢执行任务，所以将它在慢执行Map中记录一次
                        //Map的key为jobid，value为慢执行的次数
                        AtomicInteger timeoutCount = jobTimeoutCountMap.putIfAbsent(jobId, new AtomicInteger(1));
                        if (timeoutCount != null) {
                            //慢执行的次数加一
                            timeoutCount.incrementAndGet();
                        }
                    }
                }
            }
        });
    }
}