/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.test.junit4;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.apache.camel.CamelContext;
import org.apache.camel.spring.CamelBeanPostProcessor;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.ExcludingPackageScanClassResolver;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.junit.After;
import org.junit.AfterClass;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

/**
 * @version 
 */
public abstract class CamelSpringTestSupport extends CamelTestSupport {
    protected static ThreadLocal<AbstractApplicationContext> threadAppContext = new ThreadLocal<AbstractApplicationContext>();
    protected static Object lock = new Object();
    
    protected AbstractApplicationContext applicationContext;
    protected abstract AbstractApplicationContext createApplicationContext();

    /**
     * Lets post process this test instance to process any Camel annotations.
     * Note that using Spring Test or Guice is a more powerful approach.
     */
    @Override
    public void postProcessTest() throws Exception {
        super.postProcessTest();
        if (isCreateCamelContextPerClass()) {
            applicationContext = threadAppContext.get();
        }

        // use the bean post processor from camel-spring
        CamelBeanPostProcessor processor = new CamelBeanPostProcessor();
        processor.setApplicationContext(applicationContext);
        processor.setCamelContext(context);
        processor.postProcessBeforeInitialization(this, getClass().getName());
        processor.postProcessAfterInitialization(this, getClass().getName());
    }

    @Override
    public void doPreSetup() throws Exception {
        if (!"true".equalsIgnoreCase(System.getProperty("skipStartingCamelContext"))) {
            // tell camel-spring it should not trigger starting CamelContext, since we do that later
            // after we are finished setting up the unit test
            synchronized (lock) {
                SpringCamelContext.setNoStart(true);
                if (isCreateCamelContextPerClass()) {
                    applicationContext = threadAppContext.get();
                    if (applicationContext == null) {
                        applicationContext = createApplicationContext();
                        threadAppContext.set(applicationContext);
                    }
                } else {
                    applicationContext = createApplicationContext();
                }
                assertNotNull("Should have created a valid spring context", applicationContext);
                SpringCamelContext.setNoStart(false);
            }
        } else {
            log.info("Skipping starting CamelContext as system property skipStartingCamelContext is set to be true.");
        }
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();

        if (!isCreateCamelContextPerClass()) {
            if (applicationContext != null) {
                applicationContext.destroy();
                applicationContext = null;
            }
        }
    }

    @AfterClass
    public static void tearSpringDownAfterClass() throws Exception {
        if (threadAppContext.get() != null) {
            threadAppContext.get().destroy();
            threadAppContext.remove();
        }
    }
    
    /**
     * Create a parent context that initializes a
     * {@link org.apache.camel.spi.PackageScanClassResolver} to exclude a set of given classes from
     * being resolved. Typically this is used at test time to exclude certain routes,
     * which might otherwise be just noisy, from being discovered and initialized.
     * <p/>
     * To use this filtering mechanism it is necessary to provide the
     * {@link ApplicationContext} returned from here as the parent context to
     * your test context e.g.
     *
     * <pre>
     * protected AbstractXmlApplicationContext createApplicationContext() {
     *     return new ClassPathXmlApplicationContext(new String[] {&quot;test-context.xml&quot;}, getRouteExcludingApplicationContext());
     * }
     * </pre>
     *
     * This will, in turn, call the template methods <code>excludedRoutes</code>
     * and <code>excludedRoute</code> to determine the classes to be excluded from scanning.
     *
     * @return ApplicationContext a parent {@link ApplicationContext} configured
     *         to exclude certain classes from package scanning
     */
    protected ApplicationContext getRouteExcludingApplicationContext() {
        GenericApplicationContext routeExcludingContext = new GenericApplicationContext();
        routeExcludingContext.registerBeanDefinition("excludingResolver", new RootBeanDefinition(ExcludingPackageScanClassResolver.class));
        routeExcludingContext.refresh();

        ExcludingPackageScanClassResolver excludingResolver = routeExcludingContext.getBean("excludingResolver", ExcludingPackageScanClassResolver.class);
        List<Class<?>> excluded = CastUtils.cast(Arrays.asList(excludeRoutes()));
        excludingResolver.setExcludedClasses(new HashSet<Class<?>>(excluded));

        return routeExcludingContext;
    }

    /**
     * Template method used to exclude {@link org.apache.camel.Route} from the test time context
     * route scanning
     *
     * @return Class[] the classes to be excluded from test time context route scanning
     */
    protected Class<?>[] excludeRoutes() {
        Class<?> excludedRoute = excludeRoute();
        return excludedRoute != null ? new Class[] {excludedRoute} : new Class[0];
    }

    /**
     * Template method used to exclude a {@link org.apache.camel.Route} from the test camel context
     */
    protected Class<?> excludeRoute() {
        return null;
    }

    /**
     * Looks up the mandatory spring bean of the given name and type, failing if
     * it is not present or the correct type
     */
    public <T> T getMandatoryBean(Class<T> type, String name) {
        Object value = applicationContext.getBean(name);
        assertNotNull("No spring bean found for name <" + name + ">", value);
        if (type.isInstance(value)) {
            return type.cast(value);
        } else {
            fail("Spring bean <" + name + "> is not an instanceof " + type.getName() + " but is of type " + ObjectHelper.className(value));
            return null;
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // don't start the springCamelContext if we
        return SpringCamelContext.springCamelContext(applicationContext, false);
    }
}
