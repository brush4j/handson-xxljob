package com.cqfy.xxl.job.admin.core.route.strategy;

import com.cqfy.xxl.job.admin.core.route.ExecutorRouter;
import com.cqfy.xxl.job.core.biz.model.ReturnT;
import com.cqfy.xxl.job.core.biz.model.TriggerParam;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/16
 * @Description:哈希一执行路由策略
 */
public class ExecutorRouteConsistentHash extends ExecutorRouter {

    //哈希环上存储的地址的容量限制
    private static int VIRTUAL_NODE_NUM = 100;

    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/16
     * @Description:md5散列的方式计算hash值。这个是源码中的注释，我复制过来了。如果大家感兴趣的话，可以看看下面这个方法
     */
    private static long hash(String key) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not supported", e);
        }
        md5.reset();
        byte[] keyBytes = null;
        try {
            keyBytes = key.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unknown string :" + key, e);
        }
        md5.update(keyBytes);
        byte[] digest = md5.digest();
        long hashCode = ((long) (digest[3] & 0xFF) << 24)
                | ((long) (digest[2] & 0xFF) << 16)
                | ((long) (digest[1] & 0xFF) << 8)
                | (digest[0] & 0xFF);
        long truncateHashCode = hashCode & 0xffffffffL;
        return truncateHashCode;
    }


    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/16
     * @Description:这个方法的整体逻辑其实很简单，就是先计算每一个执行器地址的hash值，然后在计算定时任务id的hash值
     * 然后将定时任务的哈希值和执行器地址的哈希值做对比，获得距离定时任务id哈希值最近的那个执行器地址就行了，当然，这里要稍微形象一点
     * 定时任务的hash值构成了一个圆环，按照顺时针的方向，找到里定时任务id的哈希值最进的那个哈希值即可
     * 这里用到了TreeMap这个数据结构
     */
    public String hashJob(int jobId, List<String> addressList) {
        TreeMap<Long, String> addressRing = new TreeMap<Long, String>();
        for (String address: addressList) {
            for (int i = 0; i < VIRTUAL_NODE_NUM; i++) {
                //计算执行器地址的hash值
                long addressHash = hash("SHARD-" + address + "-NODE-" + i);
                //把地址hash值和地址放到TreeMap中
                addressRing.put(addressHash, address);
            }
        }
        //计算定时任务id的hahs值
        long jobHash = hash(String.valueOf(jobId));
        //TreeMap的tailMap方法在这里很重要，这个方法会让内部键值对的键跟jobHash做比较
        //比jobHash的值大的键，对应的键值对都会返回给用户
        //这里得到的lastRing就相当于圆环上所有比定时任务hash值大的hash值了
        SortedMap<Long, String> lastRing = addressRing.tailMap(jobHash);
        //如果不为空
        if (!lastRing.isEmpty()) {
            //取第一个就行，最接近定时任务hash值就行
            return lastRing.get(lastRing.firstKey());
        }
        //如果为空，就从addressRing中获取第一个执行器地址即可
        return addressRing.firstEntry().getValue();
    }

    @Override
    public ReturnT<String> route(TriggerParam triggerParam, List<String> addressList) {
        String address = hashJob(triggerParam.getJobId(), addressList);
        return new ReturnT<String>(address);
    }

}
