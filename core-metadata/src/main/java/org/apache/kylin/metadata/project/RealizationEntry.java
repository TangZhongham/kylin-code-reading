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

package org.apache.kylin.metadata.project;

import org.apache.kylin.metadata.realization.RealizationType;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public class RealizationEntry implements Serializable{

    @JsonProperty("type")
    private RealizationType type;

    @JsonProperty("realization")
    private String realization;

    public RealizationType getType() {
        return type;
    }

    public void setType(RealizationType type) {
        this.type = type;
    }

    public String getRealization() {
        return realization;
    }

    public void setRealization(String realization) {
        this.realization = realization;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        RealizationEntry entry = (RealizationEntry) o;

        if (realization != null ? !realization.equalsIgnoreCase(entry.realization) : entry.realization != null)
            return false;
        if (type != entry.type)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (realization != null ? realization.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "" + type.name() + "." + realization;
    }

    public static RealizationEntry create(RealizationType type, String realization) {
        RealizationEntry entry = new RealizationEntry();
        entry.setRealization(realization);
        entry.setType(type);
        return entry;
    }
}
