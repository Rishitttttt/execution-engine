package com.ocee.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

@Component
public class RequestBodyCapturingFilter extends OncePerRequestFilter {
    public static final String BODY_WRAPPER_ATTR = "ocee.requestBodyWrapper";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(req.getMethod()) && req.getRequestURI().startsWith("/api/submissions")) {
            ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(req);
            wrapper.setAttribute(BODY_WRAPPER_ATTR, wrapper);
            chain.doFilter(wrapper, res);
        } else {
            chain.doFilter(req, res);
        }
    }
}
