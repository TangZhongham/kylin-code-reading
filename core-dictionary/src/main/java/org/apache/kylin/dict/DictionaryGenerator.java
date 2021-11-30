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

package org.apache.kylin.dict;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;
import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.util.Dictionary;
import org.apache.kylin.metadata.datatype.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * @author yangli9
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class DictionaryGenerator {

    private static final Logger logger = LoggerFactory.getLogger(DictionaryGenerator.class);

    public static IDictionaryBuilder newDictionaryBuilder(DataType dataType) {
        Preconditions.checkNotNull(dataType, "dataType cannot be null");

        // build dict, case by data type
        IDictionaryBuilder builder;
        boolean useForest = KylinConfig.getInstanceFromEnv().isUseForestTrieDictionary();
        if (dataType.isNumberFamily())
            builder = useForest ? new NumberTrieDictForestBuilder() : new NumberTrieDictBuilder();
        else
            builder = useForest ? new StringTrieDictForestBuilder() : new StringTrieDictBuilder();
        return builder;
    }

    public static Dictionary<String> buildDictionary(DataType dataType, IDictionaryValueEnumerator valueEnumerator)
            throws IOException {
        return buildDictionary(newDictionaryBuilder(dataType), null, valueEnumerator);
    }

    static Dictionary<String> buildDictionary(IDictionaryBuilder builder, DictionaryInfo dictInfo,
            IDictionaryValueEnumerator valueEnumerator) throws IOException {
        int baseId = 0; // always 0 for now
        int nSamples = 5;
        ArrayList<String> samples = new ArrayList<String>(nSamples);

        // init the builder
        builder.init(dictInfo, baseId, null);

        // add values
        try {
            while (valueEnumerator.moveNext()) {
                String value = valueEnumerator.current();

                boolean accept = builder.addValue(value);

                if (accept && samples.size() < nSamples && samples.contains(value) == false)
                    samples.add(value);
            }
        } catch (IOException e) {
            logger.error("Error during adding dict value.", e);
            builder.clear();
            throw e;
        }

        // build
        Dictionary<String> dict = builder.build();
        logger.debug("Dictionary cardinality: " + dict.getSize());
        logger.debug("Dictionary builder class: " + builder.getClass().getName());
        logger.debug("Dictionary class: " + dict.getClass().getName());
        // log a few samples
        StringBuilder buf = new StringBuilder();
        for (String s : samples) {
            if (buf.length() > 0) {
                buf.append(", ");
            }
            buf.append(s.toString()).append("=>").append(dict.getIdFromValue(s));
        }
        logger.debug("Dictionary value samples: " + buf.toString());

        return dict;
    }

    public static Dictionary mergeDictionaries(DataType dataType, List<DictionaryInfo> sourceDicts) throws IOException {
        List<Dictionary<String>> dictList = Lists.transform(sourceDicts, new Function<DictionaryInfo, Dictionary<String>>() {
            @Nullable
            @Override
            public Dictionary<String> apply(@Nullable DictionaryInfo input) {
                return input.dictionaryObject;
            }
        });
        return buildDictionary(dataType, new MultipleDictionaryValueEnumerator(dataType, dictList));
    }

    private static class StringTrieDictBuilder implements IDictionaryBuilder {
        int baseId;
        TrieDictionaryBuilder builder;

        @Override
        public void init(DictionaryInfo info, int baseId, String hdfsDir) throws IOException {
            this.baseId = baseId;
            this.builder = new TrieDictionaryBuilder(new StringBytesConverter());
        }

        @Override
        public boolean addValue(String value) {
            if (value == null)
                return false;

            builder.addValue(value);
            return true;
        }

        @Override
        public Dictionary<String> build() throws IOException {
            return builder.build(baseId);
        }

        @Override
        public void clear() {

        }
    }

    private static class StringTrieDictForestBuilder implements IDictionaryBuilder {
        TrieDictionaryForestBuilder builder;

        @Override
        public void init(DictionaryInfo info, int baseId, String hdfsDir) throws IOException {
            builder = new TrieDictionaryForestBuilder(new StringBytesConverter(), baseId);
        }

        @Override
        public boolean addValue(String value) {
            if (value == null)
                return false;

            builder.addValue(value);
            return true;
        }

        @Override
        public Dictionary<String> build() throws IOException {
            return builder.build();
        }

        @Override
        public void clear() {

        }
    }

    @SuppressWarnings("deprecation")
    private static class NumberTrieDictBuilder implements IDictionaryBuilder {
        int baseId;
        NumberDictionaryBuilder builder;

        @Override
        public void init(DictionaryInfo info, int baseId, String hdfsDir) throws IOException {
            this.baseId = baseId;
            this.builder = new NumberDictionaryBuilder();
        }

        @Override
        public boolean addValue(String value) {
            if (StringUtils.isBlank(value)) // empty string is treated as null
                return false;

            builder.addValue(value);
            return true;
        }

        @Override
        public Dictionary<String> build() throws IOException {
            return builder.build(baseId);
        }

        @Override
        public void clear() {

        }
    }

    private static class NumberTrieDictForestBuilder implements IDictionaryBuilder {
        NumberDictionaryForestBuilder builder;

        @Override
        public void init(DictionaryInfo info, int baseId, String hdfsDir) throws IOException {
            builder = new NumberDictionaryForestBuilder(baseId);
        }

        @Override
        public boolean addValue(String value) {
            if (StringUtils.isBlank(value)) // empty string is treated as null
                return false;

            builder.addValue(value);
            return true;
        }

        @Override
        public Dictionary<String> build() throws IOException {
            return builder.build();
        }

        @Override
        public void clear() {

        }
    }

}
