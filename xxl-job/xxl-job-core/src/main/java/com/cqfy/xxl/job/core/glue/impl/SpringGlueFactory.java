package com.cqfy.xxl.job.core.glue.impl;

import com.cqfy.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.cqfy.xxl.job.core.glue.GlueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationUtils;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;


public class SpringGlueFactory extends GlueFactory {

    private static Logger logger = LoggerFactory.getLogger(SpringGlueFactory.class);


    @Override
    public void injectService(Object instance){
        if (instance==null) {
            return;
        }
        if (XxlJobSpringExecutor.getApplicationContext() == null) {
            return;
        }
        //得到该对象中的属性
        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field field : fields) {
            //如果是静态属性就跳过
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Object fieldBean = null;
            //其实下面都是在做属性注入的工作了，这里就是看看该属性上有没有Resource注解
            if (AnnotationUtils.getAnnotation(field, Resource.class) != null) {
                try {//如果有就得到这个注解
                    Resource resource = AnnotationUtils.getAnnotation(field, Resource.class);
                    //如果注解中有名称，就从容器中获得对应的对象
                    if (resource.name()!=null && resource.name().length()>0){
                        fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(resource.name());
                    } else {
                        //否则就直接按照属性的名称从容器中获得对象
                        fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getName());
                    }
                } catch (Exception e) {
                }
                if (fieldBean==null ) {
                    //上面都赋值失败的话，就直接按照属性的类型从容器中获得对象
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getType());
                }
            }//判断是否有Autowired注解，逻辑和上面一样，就不再重复了
            else if (AnnotationUtils.getAnnotation(field, Autowired.class) != null) {
                Qualifier qualifier = AnnotationUtils.getAnnotation(field, Qualifier.class);
                if (qualifier!=null && qualifier.value()!=null && qualifier.value().length()>0) {
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(qualifier.value());
                } else {
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getType());
                }
            }
            if (fieldBean!=null) {
                //设置可访问
                field.setAccessible(true);
                try {
                    //用反射给对象的属性赋值
                    field.set(instance, fieldBean);
                } catch (IllegalArgumentException e) {
                    logger.error(e.getMessage(), e);
                } catch (IllegalAccessException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

}
