/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the
 * NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.scr.integration;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Hashtable;

import javax.inject.Inject;

import junit.framework.TestCase;

import org.apache.felix.scr.Component;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.LogService;

@RunWith(JUnit4TestRunner.class)
public class ComponentConcurrencyTest extends ComponentTestBase
{
    static
    {
        // uncomment to enable debugging of this test class
        //  paxRunnerVmOption = DEBUG_VM_OPTION;
        descriptorFile = "/integration_test_component_concurrency.xml";
    }

    @Inject
    protected BundleContext bundleContext;

    protected static void delay(int secs)
    {
        try
        {
            Thread.sleep(secs * 1000);
        }
        catch (InterruptedException ie)
        {
        }
    }

    // Used to ignore logs displayed by the framework from stdout.
    // (the log service will log it because it listen to fwk error 
    // events ...).
    static class NullStdout extends PrintStream
    {
        NullStdout()
        {
            super(new OutputStream()
            {
                @Override
                public void write(int b) throws IOException
                {
                }
            });
        }
    }

    public static class Log implements LogListener
    {
        private volatile boolean _foundWarnings;
        private final static PrintStream _out =
                new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.err), 128));

        public void logged(LogEntry entry)
        {
            if (entry.getLevel() <= 2)
            {
                _foundWarnings = true;
            }
            StringWriter sw = new StringWriter();
            sw.append("D=");
            sw.append(new Date(entry.getTime()).toString());
            sw.append(", T=" + Thread.currentThread().getName());
            sw.append(": ");
            sw.append(entry.getMessage());
            if (entry.getException() != null)
            {
                sw.append(System.getProperty("line.separator"));
                PrintWriter pw = new PrintWriter(sw);
                entry.getException().printStackTrace(pw);
            }
            _out.println(sw.toString());
        }

        boolean foundWarnings()
        {
            return _foundWarnings;
        }
    }

    @Test
    public void test_concurrent_component_activation_using_componentFactories()
    {
        final PrintStream out = System.out;
        final PrintStream err = System.err;
        System.setOut(new NullStdout());
        System.setErr(new NullStdout());
        
        try
        {
            Log log = new Log();
            ServiceReference sr = bundleContext.getServiceReference(LogReaderService.class.getName());
            TestCase.assertNotNull(sr);
            LogReaderService logReader = (LogReaderService) bundleContext.getService(sr);
            TestCase.assertNotNull(logReader);
            logReader.addLogListener(log);

            final Component AFactory =
                    findComponentByName("org.apache.felix.scr.integration.components.concurrency.AFactory");
            TestCase.assertNotNull(AFactory);
            AFactory.enable();

            final Component CFactory =
                    findComponentByName("org.apache.felix.scr.integration.components.concurrency.CFactory");
            TestCase.assertNotNull(CFactory);
            CFactory.enable();

            delay(30);
            TestCase.assertFalse(log.foundWarnings());
        }

        finally
        {
            System.setOut(out);
            System.setErr(err);
        }
    }
}