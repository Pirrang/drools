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
package org.drools.mvel.integrationtests.concurrency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;

public class ConcurrentInsertionsToSubnetworksTest extends AbstractConcurrentInsertionsTest {

    public static Stream<Arguments> parameters() {
        List<String[]> drlParams = Arrays.asList(
                                                 new String[]{"sharedSubnetworkAccumulateRule", sharedSubnetworkAccumulateRule},
                                                 new String[]{"noSharingSubnetworkAccumulateRule", noSharingSubnetworkAccumulateRule},
                                                 new String[]{"sharedSubnetworkNotRule", sharedSubnetworkNotRule},
                                                 new String[]{"sharedSubnetworkExistsRule", sharedSubnetworkExistsRule});
        // TODO: EM failed with some tests. File JIRAs
        Collection<KieBaseTestConfiguration> kbParams = TestParametersUtil2.getKieBaseCloudConfigurations(false);
        // combine
        List<Arguments> params = new ArrayList<>();
        for (String[] drlParam : drlParams) {
            for (KieBaseTestConfiguration kbParam : kbParams) {
                params.add(Arguments.of(drlParam[1], kbParam));
            }
        }
        return params.stream();
    }


    private final static String sharedSubnetworkAccumulateRule =
        "import " + AtomicInteger.class.getCanonicalName() + ";\n" +
            "rule R1y when\n" +
            "    Number() from accumulate ( AtomicInteger() and $s : String( this == \"test_1\" ) ; count($s) )" +
            "    AtomicInteger() \n" +
            "    Long()\n" +
            "then\n" +
            "    System.out.println(\"R1y\");" +
            "end\n" +
            "\n" +
            "rule R1x when\n" +
            "    Number() from accumulate ( AtomicInteger() and $s : String( this == \"test_1\" ) ; count($s) )\n" +
            "    AtomicInteger( get() == 1 ) \n" +
            "then\n" +
            "    System.out.println(\"R1x\");" +
            "end\n" +
            "" +
            "rule R2 when\n" +
            "    $i : AtomicInteger( get() < 3 )\n" +
            "then\n" +
            "    System.out.println(\"R2\");" +
            "    $i.incrementAndGet();" +
            "    update($i);" +
            "end\n";

    private final static String noSharingSubnetworkAccumulateRule =
        "import " + AtomicInteger.class.getCanonicalName() + ";\n" +
            "rule R1y when\n" +
            "    AtomicInteger() \n" +
            "    Number() from accumulate ( AtomicInteger() and $s : String( this == \"test_1\" ) ; count($s) )" +
            "    Long()\n" +
            "then\n" +
            "    System.out.println(\"R1y\");" +
            "end\n" +
            "\n" +
            "rule R1x when\n" +
            "    AtomicInteger() \n" +
            "    Number() from accumulate ( $i : AtomicInteger( get() == 1) and String( this == \"test_2\" ) ; count($i) )\n" +
            "then\n" +
            "    System.out.println(\"R1x\");" +
            "end\n" +
            "" +
            "rule R2 when\n" +
            "    $i : AtomicInteger( get() < 3 )\n" +
            "then\n" +
            "    System.out.println(\"R2\");" +
            "    $i.incrementAndGet();" +
            "    update($i);" +
            "end\n";

    private final static String sharedSubnetworkNotRule =
        "import " + AtomicInteger.class.getCanonicalName() + ";\n" +
            "rule R1 when\n" +
            "    AtomicInteger() \n" +
            "    not(AtomicInteger( get() == 1 ) and String( this == \"test_1\" )) \n" +
            "then\n" +
            "    System.out.println(\"R1\");" +
            "end\n" +
            "\n" +
            "rule R2 when\n" +
            "    AtomicInteger() \n" +
            "    not(AtomicInteger( get() == 1 ) and String( this == \"test_1\" )) \n" +
            "    String( this != \"test_2\" ) \n" +
            "then\n" +
            "    System.out.println(\"R2\");" +
            "end\n";

    private final static String sharedSubnetworkExistsRule =
        "import " + AtomicInteger.class.getCanonicalName() + ";\n" +
            "rule R1 when\n" +
            "    AtomicInteger() \n" +
            "    exists(AtomicInteger( get() == 1 ) and String( this == \"test_1\" )) \n" +
            "then\n" +
            "    System.out.println(\"R1\");" +
            "end\n" +
            "\n" +
            "rule R2 when\n" +
            "    AtomicInteger() \n" +
            "    exists(AtomicInteger( get() == 1 ) and String( this == \"test_1\" )) \n" +
            "    String( this != \"test_2\" ) \n" +
            "then\n" +
            "    System.out.println(\"R2\");" +
            "end\n";

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(80000)
    public void testConcurrentInsertionsFewObjectsManyThreads(String drl, KieBaseTestConfiguration kieBaseTestConfiguration) throws InterruptedException {
        testConcurrentInsertions(drl, 1, 1000, false, false, kieBaseTestConfiguration);
    }

    @ParameterizedTest(name = "DRL={0}, KieBase type={2}")
    @MethodSource("parameters")
    @Timeout(80000)
    public void testConcurrentInsertionsManyObjectsFewThreads(String drl, KieBaseTestConfiguration kieBaseTestConfiguration) throws InterruptedException {
        testConcurrentInsertions(drl, 500, 4, false, false, kieBaseTestConfiguration);
    }

    @ParameterizedTest(name = "DRL={0}, KieBase type={2}")
    @MethodSource("parameters")
    @Timeout(80000)
    public void testConcurrentInsertionsManyObjectsSingleThread(String drl, KieBaseTestConfiguration kieBaseTestConfiguration) throws InterruptedException {
        testConcurrentInsertions(drl, 1000, 1, false, false, kieBaseTestConfiguration);
    }

    @ParameterizedTest(name = "DRL={0}, KieBase type={2}")
    @MethodSource("parameters")
    @Timeout(80000)
    public void testConcurrentInsertionsNewSessionEachThread(String drl, KieBaseTestConfiguration kieBaseTestConfiguration) throws InterruptedException {
        testConcurrentInsertions(drl, 10, 1000, true, false, kieBaseTestConfiguration);
    }

    @ParameterizedTest(name = "DRL={0}, KieBase type={2}")
    @MethodSource("parameters")
    @Timeout(80000)
    public void testConcurrentInsertionsNewSessionEachThreadUpdate(String drl, KieBaseTestConfiguration kieBaseTestConfiguration) throws InterruptedException {
        testConcurrentInsertions(drl, 10, 1000, true, true, kieBaseTestConfiguration);
    }

    protected Callable<Boolean> getTask(
        final int objectCount,
        final KieSession ksession,
        final boolean disposeSession,
        final boolean updateFacts) {
        return () -> {
            try {
                for (int j = 0; j < 10; j++) {
                    final FactHandle[] facts = new FactHandle[objectCount];
                    final FactHandle[] stringFacts = new FactHandle[objectCount];
                    for (int i = 0; i < objectCount; i++) {
                        facts[i] = ksession.insert(new AtomicInteger(i));
                        stringFacts[i] = ksession.insert("test_" + i);
                    }
                    if (updateFacts) {
                        for (int i = 0; i < objectCount; i++) {
                            ksession.update(facts[i], new AtomicInteger(-i));
                            ksession.update(stringFacts[i], "updated_test_" + i);
                        }
                    }
                    for (int i = 0; i < objectCount; i++) {
                        ksession.delete(facts[i]);
                        ksession.delete(stringFacts[i]);
                    }
                    ksession.fireAllRules();
                }
                return true;
            } catch (final Exception e) {
                e.printStackTrace();
                return false;
            } finally {
                if (disposeSession) {
                    ksession.dispose();
                }
            }
        };
    }
}
