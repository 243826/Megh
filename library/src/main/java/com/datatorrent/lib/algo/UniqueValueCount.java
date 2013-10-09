/*
 * Copyright (c) 2013 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.lib.algo;

import com.datatorrent.api.*;
import com.datatorrent.api.annotation.InputPortFieldAnnotation;
import com.datatorrent.api.annotation.OutputPortFieldAnnotation;
import com.datatorrent.lib.util.KeyValPair;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

/**
 *
 * Counts no. of unique values of a key within a window.<br>
 * Emits {@link InternalCountOutput} which contains the key, count of its unique values
 * and also the set of values.<br>
 * When the operator is partitioned, the unifier uses the internal set of values to
 * compute the count of unique values again.<br>
 * <br>
 * Partitions: yes, uses {@link UniqueCountUnifier} to merge partitioned output.<br>
 * Stateful: no<br>
 * <br></br>
 *
 * @param <K>Type of Key objects</K>
 * @param <V>Type of Value objects</V>
 *
 * @since 0.3.5
 */
public class UniqueValueCount<K,V> extends BaseOperator {

    private final Map<K,Set<V>>  interimUniqueValues;


    @InputPortFieldAnnotation(name="inputPort")
    public transient DefaultInputPort<KeyValPair<K,V>> inputPort = new DefaultInputPort<KeyValPair<K, V>>() {
        @Override
        public void process(KeyValPair<K, V> pair) {
            Set<V> values= interimUniqueValues.get(pair.getKey());
            if(values==null){
                values=Sets.newHashSet();
                interimUniqueValues.put(pair.getKey(),values);
            }
            values.add(pair.getValue());
        }
    } ;

    @OutputPortFieldAnnotation(name="outputPort")
    public transient DefaultOutputPort<KeyValPair<K,Integer>> outputPort= new DefaultOutputPort<KeyValPair<K, Integer>>(){

        @Override
        public Unifier<KeyValPair<K,Integer>> getUnifier() {
            return new UniqueCountUnifier<K,V>();
        }
    };

    public UniqueValueCount (){
        this.interimUniqueValues=Maps.newHashMap();
    }


    @Override
    public void endWindow() {
        for (K key : interimUniqueValues.keySet()) {
            Set<V> values= interimUniqueValues.get(key);
            outputPort.emit(new InternalCountOutput<K, V>(key, values.size(),values));
        }
        interimUniqueValues.clear();
    }

    /**
     * State which contains a key, a set of values of that key, and a count of unique values of that key.<br></br>
     *
     * @param <K>Type of key objects</K>
     * @param <V>Type of value objects</V>
     */
    public static class InternalCountOutput<K,V> extends KeyValPair<K,Integer> {

        private final Set<V> interimUniqueValues;

        private InternalCountOutput(){
            this(null,null,null);
        }

        private InternalCountOutput(K k, Integer count, Set<V> interimUniqueValues){
            super(k,count);
            this.interimUniqueValues=interimUniqueValues;
        }

        public Set<V> getInternalSet(){
            return interimUniqueValues;
        }

        @Override
        public String toString(){
            return super.toString();
        }
    }

    /**
     * Unifier for {@link UniqueValueCount} operator.<br>
     * It uses the internal set of values emitted by the operator and
     * emits {@link KeyValPair} of the key and its unique count.<br></br>
     * @param <K>Type of Key objects</K>
     * @param <V>Type of Value objects</V>
     */
    public static class UniqueCountUnifier<K,V> implements Unifier<KeyValPair<K,Integer>> {

        public final transient DefaultOutputPort<KeyValPair<K,Integer>> outputPort = new DefaultOutputPort<KeyValPair<K, Integer>>();

        private final Map<K,Set<V>> finalUniqueValues;

        public UniqueCountUnifier(){
            this.finalUniqueValues=Maps.newHashMap();
        }

        @Override
        public void process(KeyValPair<K,Integer> uniquePairFromPartitions) {
            if(uniquePairFromPartitions instanceof InternalCountOutput) {
                InternalCountOutput<K,V> pairList= (InternalCountOutput<K,V>)uniquePairFromPartitions;
                Set<V> values= finalUniqueValues.get(pairList.getKey());
                if(values==null){
                    values=Sets.newHashSet();
                    finalUniqueValues.put(pairList.getKey(),values);
                }
                values.addAll(pairList.interimUniqueValues);
            }
        }

        @Override
        public void beginWindow(long l) {
        }

        @Override
        public void endWindow() {
            for(K key: finalUniqueValues.keySet()){
                outputPort.emit(new KeyValPair<K, Integer>(key,finalUniqueValues.get(key).size()));
            }
            finalUniqueValues.clear();
        }

        @Override
        public void setup(Context.OperatorContext operatorContext) {
        }

        @Override
        public void teardown() {
        }
    }
}
