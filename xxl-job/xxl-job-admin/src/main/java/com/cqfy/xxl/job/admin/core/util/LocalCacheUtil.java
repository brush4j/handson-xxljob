package com.cqfy.xxl.job.admin.core.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author:halfmoonly
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/12
 * @Description:本地缓存的工具类
 */
public class LocalCacheUtil {

    //这个Map就是缓存数据的容器
    private static ConcurrentMap<String, LocalCacheData> cacheRepository = new ConcurrentHashMap<String, LocalCacheData>();

    //内部类，其实存储的键值对，要放在这个内部类中，Map中存储的value是这个类的对象
    private static class LocalCacheData{
        private String key;
        private Object val;
        private long timeoutTime;

        public LocalCacheData() {
        }

        public LocalCacheData(String key, Object val, long timeoutTime) {
            this.key = key;
            this.val = val;
            this.timeoutTime = timeoutTime;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public Object getVal() {
            return val;
        }

        public void setVal(Object val) {
            this.val = val;
        }

        public long getTimeoutTime() {
            return timeoutTime;
        }

        public void setTimeoutTime(long timeoutTime) {
            this.timeoutTime = timeoutTime;
        }
    }


    /**
     * @author:halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/12
     * @Description:存储键值对的方法
     */
    public static boolean set(String key, Object val, long cacheTime){
        //先清除一次缓存超时的数据
        cleanTimeoutCache();
        //对key-value-缓存时间做判空
        if (key==null || key.trim().length()==0) {
            return false;
        }
        if (val == null) {
            remove(key);
        }
        if (cacheTime <= 0) {
            remove(key);
        }
        //获取当前缓存数据的超时时间
        long timeoutTime = System.currentTimeMillis() + cacheTime;
        //创建缓存键值对的对象
        LocalCacheData localCacheData = new LocalCacheData(key, val, timeoutTime);
        //放入Map中
        cacheRepository.put(localCacheData.getKey(), localCacheData);
        return true;
    }


    /**
     * @author:halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/12
     * @Description:删除键值对
     */
    public static boolean remove(String key){
        if (key==null || key.trim().length()==0) {
            return false;
        }
        cacheRepository.remove(key);
        return true;
    }

    /**
     * @author:halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/12
     * @Description:获取数据
     */
    public static Object get(String key){
        if (key==null || key.trim().length()==0) {
            return null;
        }
        LocalCacheData localCacheData = cacheRepository.get(key);
        if (localCacheData!=null && System.currentTimeMillis()<localCacheData.getTimeoutTime()) {
            return localCacheData.getVal();
        } else {
            remove(key);
            return null;
        }
    }


    /**
     * @author:halfmoonly
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/12
     * @Description:清楚超时的缓存键值对
     */
    public static boolean cleanTimeoutCache(){
        //先判断Map中是否有数据
        if (!cacheRepository.keySet().isEmpty()) {
            //如果有数据就遍历键值对
            for (String key: cacheRepository.keySet()) {
                //获取每一个LocalCacheData对象
                LocalCacheData localCacheData = cacheRepository.get(key);
                //判断LocalCacheData对象中的超时时间是否到了
                if (localCacheData!=null && System.currentTimeMillis()>=localCacheData.getTimeoutTime()) {
                    //如果到达超时时间就删除键值对
                    cacheRepository.remove(key);
                }
            }
        }
        return true;
    }

}
