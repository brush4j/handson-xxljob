package com.cqfy.xxl.job.core.enums;

/**
 * @author:Halfmoonly
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/12
 * @Description:阻塞处理策略，在第一版本中也没有体现出该类中的策略
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
