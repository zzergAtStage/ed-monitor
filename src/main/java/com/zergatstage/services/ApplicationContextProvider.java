package com.zergatstage.services;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * A helper class that holds the Spring ApplicationContext.
 * <p>
 * This class allows non–Spring-managed classes to retrieve beans from the context.
 * </p>
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {

    /**
     * The static ApplicationContext instance.
     */
    private static ApplicationContext context;

    /**
     * Sets the ApplicationContext.
     *
     * @param ctx the ApplicationContext
     */
    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        context = ctx;
    }

    /**
     * Returns the Spring ApplicationContext.
     *
     * @return the ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        return context;
    }
}

