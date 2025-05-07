package com.cqfy.xxl.job.admin.thread;

import com.cqfy.xxl.job.admin.cron.CronExpression;
import com.cqfy.xxl.job.admin.dao.XxlJobInfo;
import com.cqfy.xxl.job.admin.dao.XxlJobInfoDao;
import com.cqfy.xxl.job.admin.dao.XxlJobInfoDaoImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class JobScheduleHelper {

    private static Logger logger = LoggerFactory.getLogger(JobScheduleHelper.class);
    public static XxlJobInfoDao dao = new XxlJobInfoDaoImpl();

    /**
     * 执行器方业务逻辑
     */
    public static void selfishHeart() {
        System.out.println("执行定时任务！");
    }
    //调度定时任务的线程
    private Thread scheduleThread;

    //这个就是时间轮线程
    //这个时间轮线程就是用来主要向触发器线程池提交触发任务的
    //它提交的任务是从Map中获得的，而Map中的任务是由上面的调度线程添加的，具体逻辑会在下面的代码中讲解
    private Thread ringThread;

    //这个就是时间轮的容器，该容器中的数据是由scheduleThread线程添加的
    //但是移除是由ringThread线程移除的
    //Map的key为时间轮中任务的执行时间，也就是在时间轮中的刻度，value是需要执行的定时任务的集合，这个集合中的数据就是需要执行的定时任务的id
    //意思就是在这个时间，有这么多定时任务要被提交给调度线程池
    private volatile static Map<Integer, List<Integer>> ringData = new ConcurrentHashMap<>();

    //下面这两个是纯粹的标记，就是用来判断线程是否停止的
    private volatile boolean scheduleThreadToStop = false;
    private volatile boolean ringThreadToStop = false;
    //创建当前类的对象
    private static JobScheduleHelper instance = new JobScheduleHelper();

    //把当前类的对象暴露出去
    public static JobScheduleHelper getInstance(){
        return instance;
    }

    //这里定义了5000毫秒，查询数据库的时候会用到，查询的就是当前时间5秒之内的可以执行的
    //定时任务信息
    public static final long PRE_READ_MS = 5000;

    //启动调度线程工作的方法
    public void start(){

        scheduleThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!scheduleThreadToStop) {
                    //得到调度任务的开始时间
                    long start = System.currentTimeMillis();
                    //这个变量下面要用到，就是用来判断是否从数据库中读取到了数据，读取到了就意味着有任务要执行
                    //这里默认为true
                    boolean preReadSuc = true;
                    //得到当前时间
                    long nowTime = System.currentTimeMillis();
                    //从数据库中根据执行时间查询定时任务的方法
                    List<XxlJobInfo> jobInfoList = dao.scheduleJobQuery(nowTime + PRE_READ_MS);
                    //判空操作
                    if (jobInfoList!=null && jobInfoList.size()>0) {
                        //遍历所有定时任务信息
                        for (XxlJobInfo jobInfo : jobInfoList) {
                            //这里做了一个判断，刚才得到的当前时间，是不是大于任务的下一次执行时间加上5秒，为什么会出现这种情况呢？
                            //让我们仔细想一想，本来，一个任务被调度执行了，就会计算出它下一次的执行时机，然后更新数据库中的任务的下一次执行时间
                            //但请大家思考另外一种情况，如果服务器宕机了呢？本来上一次要执行的任务，却没有执行，比如这个任务要在第5秒执行，但是服务器在第4秒宕机了
                            //重新恢复运行后，已经是第12秒了，现在去数据库中查询任务，12 > 5 + 5，就是if括号中的不等式，这样一来，是不是就查到了执行时间比当前时间还小的任务
                            //并且已经超过当前的5秒调度周期了
                            if (nowTime > jobInfo.getTriggerNextTime() + PRE_READ_MS) {
                                //既然有过期的任务，在这里立刻调度一次任务
                                JobTriggerPoolHelper.trigger(jobInfo);
                                //在这里把过期任务的下次执行时间刷新一下，放到下一次来执行，因为定时任务已经严重过期了，所以计算一下次的执行时间
                                //就应该以当前时间为标志了
                                refreshNextValidTime(jobInfo, new Date());
                            }
                            //这里得到的就是要执行的任务的下一次执行时间同样也小于了当前时间，但是这里和上面的不同是，没有超过当前时间加5秒的那个时间
                            //现在大家应该都清楚了，上面加的那个5秒实际上就是调度周期，每一次处理的任务都是当前任务加5秒这个时间段内的
                            //这一次得到的任务仅仅是小于当前时间，但是并没有加上5秒，说明这个任务虽然过期了但仍然是在当前的调度周期中
                            //比如说这个任务要在第2秒执行，但是服务器在第1秒宕机了，恢复之后已经是第4秒了，现在任务的执行时间小于了当前时间，但是仍然在5秒的调度器内
                            //所以调度执行即可
                            else if (nowTime > jobInfo.getTriggerNextTime()) {
                                //把任务交给触发器去远程调用
                                JobTriggerPoolHelper.trigger(jobInfo);
                                //刷新该任务下一次的执行时间，也是过期任务，所以也已当前时间为标准来计算下一次执行时间
                                refreshNextValidTime(jobInfo, new Date());
                                //下面这个分之中的任务就是比较正常的，但是又有些特殊的，
                                //判断这个任务的下一次执行时间是否小于这个执行周期，注意，上面的refreshNextValidTime方法已经把该任务的
                                //下一次执行时间更新了。如果更新后的时间仍然小于执行周期，说明这个任务会在执行周期中再执行一次，当然，也可能会执行多次，
                                //这时候，就不让调度线程来处理这个任务了，而是把它提交给时间轮，让时间轮去执行。
                                if (nowTime + PRE_READ_MS > jobInfo.getTriggerNextTime()) {
                                    //计算该任务要放在时间轮的刻度，也就是在时间轮中的执行时间，注意哦，千万不要被这里的取余给搞迷惑了
                                    //这里的余数计算结果为0-59，单位是秒，意味着时间轮有60个刻度，一个代表一秒。
                                    //调度线程是按调度周期来处理任务的，举个例子，调度线程从0秒开始启动，第5秒为一个周期，把这5秒要执行的任务交给时间轮了
                                    //就去处理下一个调度周期，千万不要把调度线程处理调度任务时不断增加的调度周期就是增长的时间，调度线程每次扫描数据库不会耗费那么多时间
                                    //这个时间是作者自己设定的，并且调度线程也不是真的只按整数5秒去调度任务
                                    //实际上，调度线程从0秒开始工作，扫描0-5秒的任务，调度这些任务耗费了1秒，再次循环时，调度线程就会从1秒开始，处理1-6秒的任务
                                    //虽说是1-6秒，但是1-5秒的任务都被处理过了，但是请大家想一想，有些任务也仅仅只是被执行了一次，如果有一个任务在0-5秒调度器内被执行了
                                    //但是该任务每1秒执行一次，从第1秒开始m，那它是不是会在调度期内执行多次？可是上一次循环它可能最多只被执行了两次，一次在调度线程内，一次在时间轮内
                                    //还有几次并未执行呢，所以要交给下一个周期去执行，但是这时候它的下次执行时间还在当前时间的5秒内，如果下个周期直接从6秒开始
                                    //这个任务就无法执行了，大家可以仔细想想这个过程
                                    //时间轮才是真正按照时间增长的速度去处理定时任务的
                                    int ringSecond = (int)((jobInfo.getTriggerNextTime()/1000)%60);
                                    //把定时任务的信息，就是它的id放进时间轮
                                    pushTimeRing(ringSecond, jobInfo.getId());
                                    //刷新定时任务的下一次的执行时间，注意，这里传进去的就不再是当前时间了，而是定时任务现在的下一次执行时间
                                    //因为放到时间轮中就意味着它要执行了，所以计算新的执行时间就行了
                                    refreshNextValidTime(jobInfo, new Date(jobInfo.getTriggerNextTime()));
                                }
                            }
                            //最后，这里得到的就是最正常的任务，也就是执行时间在当前时间之后，但是又小于执行周期的任务
                            //上面的几个判断，都是当前时间大于任务的下次执行时间，实际上都是在过期的任务中做判断
                            else {
                                //这样的任务就很好处理了，反正都是调度周期，也就是当前时间5秒内要执行的任务，所以直接放到时间轮中就行
                                //计算出定时任务在时间轮中的刻度，其实就是定时任务执行的时间对应的秒数
                                //随着时间流逝，时间轮也是根据当前时间秒数来获取要执行的任务的，所以这样就可以对应上了
                                int ringSecond = (int)((jobInfo.getTriggerNextTime()/1000)%60);
                                //放进时间轮中
                                pushTimeRing(ringSecond, jobInfo.getId());
                                //刷新定时任务下一次的执行时间
                                refreshNextValidTime(jobInfo, new Date(jobInfo.getTriggerNextTime()));

                            }
                        }
                        //最后再更一下所有的任务的数据库信息
                        for (XxlJobInfo jobInfo: jobInfoList) {
                            dao.save(jobInfo);
                        }
                    }
                    else {
                        //走到这里，说明根本就没有从数据库中扫描到任何任务，把preReadSuc设置为false
                        preReadSuc = false;
                    }

                    //再次得到当然时间，然后减去开始执行扫面数据库任务的开始时间
                    //就得到了执行扫面数据库，并且调度任务的总耗时
                    long cost = System.currentTimeMillis()-start;
                    //这里有一个判断，1000毫秒就是1秒，如果总耗时小于1秒，就默认数据库中可能没多少数据
                    //线程就不必工作得那么繁忙，所以下面要让线程休息一会，然后再继续工作
                    if (cost < 1000) {
                        try {
                            //下面有一个三元运算，判断preReadSuc是否为true，如果扫描到数据了，就让该线程小睡一会儿，最多睡1秒
                            //如果根本就没有数据，就说明5秒的调度器内没有任何任务可以执行，那就让线程最多睡5秒，把时间睡过去，过5秒再开始工作
                            TimeUnit.MILLISECONDS.sleep((preReadSuc?1000:PRE_READ_MS) - System.currentTimeMillis()%1000);
                        } catch (InterruptedException e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }
            }
        });
        //启动调度线程
        scheduleThread.start();


        //在这里创建时间轮线程，并且启动
        ringThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!ringThreadToStop) {
                    try {
                        //这里让线程睡一会，作用还是比较明确的，因为该线程是时间轮线程，时间轮执行任务是按照时间刻度来执行的
                        //如果这一秒内的所有任务都调度完了，但是耗时只用了500毫秒，剩下的500毫秒就只好睡过去，等待下一个整秒到来
                        //再继续开始工作。System.currentTimeMillis() % 1000计算出来的结果如果是500毫秒，1000-500=500
                        //线程就继续睡500毫秒，如果System.currentTimeMillis() % 1000计算出来的是0，说明现在是整秒，那就睡1秒，等到下个
                        //工作时间再开始工作
                        TimeUnit.MILLISECONDS.sleep(1000 - System.currentTimeMillis() % 1000);
                    } catch (InterruptedException e) {
                        if (!ringThreadToStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                    try {
                        //先定义一个集合变量，刚才已经强调过了，时间轮是一个Map容器，Map的key是定时任务要执行的时间，value是定时任务的JobID的集合
                        //到了固定的时间，要把对应时刻的定时任务从集合中取出来，所以自然也要用集合来存放这些定时任务的ID
                        List<Integer> ringItemData = new ArrayList<>();
                        //获取当前时间的秒数
                        int nowSecond = Calendar.getInstance().get(Calendar.SECOND);
                        //下面这里很有意思，如果我们计算出来的是第3秒，时间轮线程会把第2秒，和第3秒的定时任务都取出来，一起执行
                        //这里肯定会让大家感到困惑，时间轮不是按照刻度走的吗？如果走到3秒的刻度，说明2秒的任务已经执行完了，为什么还要再拿出来？
                        //这是因为考虑到定时任务的调度情况了，如果时间轮某个刻度对应的定时任务太多，本来该最多1秒就调度完的，结果调度了2秒，直接把下一个刻度跳过了
                        //这样不就出错了？所以，每次执行的时候要把前一秒的也取出来，检查一下看是否有任务，这也算是一个兜底的方法
                        for (int i = 0; i < 2; i++) {
                            //循环了两次，第一次取出当前刻度的任务，第二次取出前一刻度的任务
                            //注意，这里取出的时候，定时任务就从时间轮中被删除了
                            List<Integer> tmpData = ringData.remove( (nowSecond+60-i)%60 );
                            if (tmpData != null) {
                                //把定时任务的ID数据添加到上面定义的集合中
                                ringItemData.addAll(tmpData);
                            }
                        }
                        //判空操作
                        if (ringItemData.size() > 0) {
                            for (int jobId: ringItemData) {
                                //在for循环中处理定时任务，让触发器线程池开始远程调用这些任务
//                                JobTriggerPoolHelper.trigger(jobInfo);
                            }
                            //最后清空集合
                            ringItemData.clear();
                        }
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        });
        ringThread.start();
    }

    //把定时任务放到时间轮中
    private void pushTimeRing(int ringSecond, int jobId){
        List<Integer> ringItemData = ringData.get(ringSecond);
        if (ringItemData == null) {
            ringItemData = new ArrayList<Integer>();
            ringData.put(ringSecond, ringItemData);
        }
        ringItemData.add(jobId);
    }

    //计算定时任务下一次执行时间的方法
    private void refreshNextValidTime(XxlJobInfo jobInfo, Date fromTime)  {
        Date nextValidTime = generateNextValidTime(jobInfo, fromTime);
        if (nextValidTime != null) {
            jobInfo.setTriggerLastTime(jobInfo.getTriggerNextTime());
            jobInfo.setTriggerNextTime(nextValidTime.getTime());
        } else {
            jobInfo.setTriggerStatus(0);
            jobInfo.setTriggerLastTime(0);
            jobInfo.setTriggerNextTime(0);
            logger.warn(">>>>>>>>>>> xxl-job, refreshNextValidTime fail for job: jobId={}, scheduleType={}, scheduleConf={}",
                    jobInfo.getId(), jobInfo.getScheduleType(), jobInfo.getScheduleConf());
        }
    }

    //利用cron表达式，计算定时任务下一次执行时间的方法
    public static Date generateNextValidTime(XxlJobInfo jobInfo, Date fromTime) {
        Date nextValidTime = null;
        try {
            nextValidTime = new CronExpression(jobInfo.getScheduleConf()).getNextValidTimeAfter(fromTime);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        return nextValidTime;
    }

}