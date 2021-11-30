/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kylin.job.util;

import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;

public class ThrowableUtils {

    private ThrowableUtils() {
        // Utils class
    }

    public static boolean isInterruptedException(Throwable e) {
        while (e != null) {
            if (e instanceof InterruptedException || e instanceof InterruptedIOException
                    || e instanceof ClosedByInterruptException) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }
}
