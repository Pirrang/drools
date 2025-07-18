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
package org.kie.dmn.feel.runtime.functions.interval;

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELBooleanFunction;
import org.kie.dmn.feel.runtime.Range;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;
import org.kie.dmn.feel.runtime.functions.BaseFEELFunction;
import org.kie.dmn.feel.runtime.functions.FEELFnResult;
import org.kie.dmn.feel.runtime.functions.ParameterName;

public class CoincidesFunction
        extends BaseFEELFunction implements FEELBooleanFunction {

    public static final CoincidesFunction INSTANCE = new CoincidesFunction();

    private CoincidesFunction() {
        super( "coincides" );
    }

    public FEELFnResult<Boolean> invoke(@ParameterName( "point1" ) Comparable point1, @ParameterName( "point2" ) Comparable point2) {
        if ( point1 == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "point1", "cannot be null"));
        }
        if ( point2 == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "point2", "cannot be null"));
        }
        try {
            boolean result = point1.compareTo( point2 ) == 0;
            return FEELFnResult.ofResult( result );
        } catch( Exception e ) {
            // points are not comparable
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "point1", "cannot be compared to point2"));
        }
    }

    public FEELFnResult<Boolean> invoke(@ParameterName( "range1" ) Range range1, @ParameterName( "range2" ) Range range2) {
        if ( range1 == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "range1", "cannot be null"));
        }
        if ( range2 == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "range2", "cannot be null"));
        }
        try {
            boolean result = range1.equals( range2 );
            return FEELFnResult.ofResult( result );
        } catch( Exception e ) {
            // points are not comparable
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "range1", "cannot be compared to range2"));
        }
    }

}
