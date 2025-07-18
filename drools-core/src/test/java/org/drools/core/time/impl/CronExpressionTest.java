/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.drools.core.time.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class CronExpressionTest extends SerializationTestSupport {
    private static final String[] VERSIONS = new String[] {"1.5.2"};

    private static final TimeZone EST_TIME_ZONE = TimeZone.getTimeZone("US/Eastern");

    /**
     * Get the object to serialize when generating serialized file for future
     * tests, and against which to validate deserialized object.
     */
    protected Object getTargetObject() throws ParseException {
        CronExpression cronExpression = new CronExpression("0 15 10 * * ? 2005");
        cronExpression.setTimeZone(EST_TIME_ZONE);
        
        return cronExpression;
    }
    
    /**
     * Get the Quartz versions for which we should verify
     * serialization backwards compatibility.
     */
    protected String[] getVersions() {
        return VERSIONS;
    }
    
    /**
     * Verify that the target object and the object we just deserialized 
     * match.
     */
    protected void verifyMatch(Object target, Object deserialized) {
        CronExpression targetCronExpression = (CronExpression)target;
        CronExpression deserializedCronExpression = (CronExpression)deserialized;
        
        assertThat(deserializedCronExpression).isNotNull();
        assertThat(deserializedCronExpression.getCronExpression()).isEqualTo(targetCronExpression.getCronExpression());
        assertThat(deserializedCronExpression.getTimeZone()).isEqualTo(targetCronExpression.getTimeZone());
    }
    
    /*
     * Test method for 'org.quartz.CronExpression.isSatisfiedBy(Date)'.
     */
    @Test
    public void testIsSatisfiedBy() throws Exception {
        CronExpression cronExpression = new CronExpression("0 15 10 * * ? 2005");
        
        Calendar cal = Calendar.getInstance();
        
        cal.set(2005, Calendar.JUNE, 1, 10, 15, 0);
        assertThat(cronExpression.isSatisfiedBy(cal.getTime())).isTrue();
        
        cal.set(Calendar.YEAR, 2006);
        assertThat(cronExpression.isSatisfiedBy(cal.getTime())).isFalse();

        cal = Calendar.getInstance();
        cal.set(2005, Calendar.JUNE, 1, 10, 16, 0);
        assertThat(cronExpression.isSatisfiedBy(cal.getTime())).isFalse();

        cal = Calendar.getInstance();
        cal.set(2005, Calendar.JUNE, 1, 10, 14, 0);
        assertThat(cronExpression.isSatisfiedBy(cal.getTime())).isFalse();
    }

    /*
     * QUARTZ-571: Showing that expressions with months correctly serialize.
     */
    @Test
    public void testQuartz571() throws Exception {
        CronExpression cronExpression = new CronExpression("19 15 10 4 Apr ? ");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(cronExpression);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        CronExpression newExpression = (CronExpression) ois.readObject();

        assertThat(cronExpression.getCronExpression()).isEqualTo(newExpression.getCronExpression());

        // if broken, this will throw an exception
        newExpression.getNextValidTimeAfter(new Date());
    }

    /*
     * QUARTZ-574: Showing that storeExpressionVals correctly calculates the month number
     */
    @Test
    public void testQuartz574() {
        try {
            CronExpression cronExpression = new CronExpression("* * * * Foo ? ");
            fail("Expected ParseException did not fire for non-existent month");
        } catch(ParseException pe) {
            assertThat(pe.getMessage().startsWith("Invalid Month value:")).as("Incorrect ParseException thrown").isTrue();
        }

        try {
            CronExpression cronExpression = new CronExpression("* * * * Jan-Foo ? ");
            fail("Expected ParseException did not fire for non-existent month");
        } catch(ParseException pe) {
            assertThat(pe.getMessage().startsWith("Invalid Month value:")).as("Incorrect ParseException thrown").isTrue();
        }
    }

    @Test
    public void testQuartz621() {
        try {
            CronExpression cronExpression = new CronExpression("0 0 * * * *");
            fail("Expected ParseException did not fire for wildcard day-of-month and day-of-week");
        } catch(ParseException pe) {
            assertThat(pe.getMessage().startsWith("Support for specifying both or none of day-of-week AND a day-of-month parameters is not implemented.")).as("Incorrect ParseException thrown").isTrue();
        }
        try {
            CronExpression cronExpression = new CronExpression("0 0 * 4 * *");
            fail("Expected ParseException did not fire for specified day-of-month and wildcard day-of-week");
        } catch(ParseException pe) {
            assertThat(pe.getMessage().startsWith("Support for specifying both or none of day-of-week AND a day-of-month parameters is not implemented.")).as("Incorrect ParseException thrown").isTrue();
        }
        try {
            CronExpression cronExpression = new CronExpression("0 0 * * * 4");
            fail("Expected ParseException did not fire for wildcard day-of-month and specified day-of-week");
        } catch(ParseException pe) {
            assertThat(pe.getMessage().startsWith("Support for specifying both or none of day-of-week AND a day-of-month parameters is not implemented.")).as("Incorrect ParseException thrown").isTrue();
        }
    }

    @Test
    public void testQuartz640() throws ParseException {
        try {
            CronExpression cronExpression = new CronExpression("0 43 9 1,5,29,L * ?");
            fail("Expected ParseException did not fire for L combined with other days of the month");
        } catch(ParseException pe) {
            assertThat(pe.getMessage().startsWith("Support for specifying 'L' and 'LW' with other days of the month is not implemented")).as("Incorrect ParseException thrown").isTrue();
        }
        try {
            CronExpression cronExpression = new CronExpression("0 43 9 ? * SAT,SUN,L");
            fail("Expected ParseException did not fire for L combined with other days of the week");
        } catch(ParseException pe) {
            assertThat(pe.getMessage().startsWith("Support for specifying 'L' with other days of the week is not implemented")).as("Incorrect ParseException thrown").isTrue();
        }
        try {
            CronExpression cronExpression = new CronExpression("0 43 9 ? * 6,7,L");
            fail("Expected ParseException did not fire for L combined with other days of the week");
        } catch(ParseException pe) {
            assertThat(pe.getMessage().startsWith("Support for specifying 'L' with other days of the week is not implemented")).as("Incorrect ParseException thrown").isTrue();
        }
        try {
            CronExpression cronExpression = new CronExpression("0 43 9 ? * 5L");
        } catch(ParseException pe) {
            fail("Unexpected ParseException thrown for supported '5L' expression.");
        }
    }
    
    @Test 
    @Disabled
    public void testSerialization() {
        // TODO as we don't want to worry about this for now
    }

}
