package com.cqfy.xxl.job.admin.core.alarm;

import com.cqfy.xxl.job.admin.core.model.XxlJobInfo;
import com.cqfy.xxl.job.admin.core.model.XxlJobLog;


public interface JobAlarm {

    public boolean doAlarm(XxlJobInfo info, XxlJobLog jobLog);

}
