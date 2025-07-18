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
package org.drools.verifier.equivalence;

import org.drools.verifier.Verifier;
import org.drools.verifier.builder.VerifierBuilder;
import org.drools.verifier.builder.VerifierBuilderFactory;
import org.drools.verifier.data.VerifierReport;
import org.drools.verifier.report.components.MessageType;
import org.drools.verifier.report.components.Severity;
import org.drools.verifier.report.components.VerifierMessageBase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kie.internal.io.ResourceFactory;
import org.kie.api.io.ResourceType;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

public class EquivalentRulesTest {

    @Test
    @Disabled("08-APR-2011 temporally ignoring -Rikkola-")
    void testVerifierLiteralRestrictionRedundancy() throws Exception {

        VerifierBuilder vBuilder = VerifierBuilderFactory.newVerifierBuilder();

        Verifier verifier = vBuilder.newVerifier();

        verifier.addResourcesToVerify(ResourceFactory.newClassPathResource("EquivalentRules.drl",
                        getClass()),
                ResourceType.DRL);

        //        for ( VerifierError error : verifier.getMissingClasses() ) {
        //            System.out.println( error.getMessage() );
        //        }

        assertThat(verifier.hasErrors()).isFalse();

        boolean noProblems = verifier.fireAnalysis();
        assertThat(noProblems).isTrue();

        VerifierReport result = verifier.getResult();

        Collection<VerifierMessageBase> warnings = result.getBySeverity(Severity.WARNING);

        int counter = 0;
        for (VerifierMessageBase message : warnings) {
            //            System.out.println( message );
            if (message.getMessageType().equals(MessageType.EQUIVALANCE)) {
                //                                System.out.println( message );
                counter++;
            }
        }

        // Has at least one item.
        assertThat(counter).isEqualTo(1);

        verifier.dispose();
    }

}
