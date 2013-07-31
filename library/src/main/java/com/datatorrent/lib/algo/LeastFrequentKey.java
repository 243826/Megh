/*
 * Copyright (c) 2013 Malhar Inc. ALL Rights Reserved.
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

import java.util.ArrayList;
import java.util.HashMap;

import com.datatorrent.api.DefaultInputPort;
import com.datatorrent.api.DefaultOutputPort;
import com.datatorrent.api.annotation.InputPortFieldAnnotation;
import com.datatorrent.api.annotation.OutputPortFieldAnnotation;
import com.datatorrent.lib.util.AbstractBaseFrequentKey;

/**
 * <p>
 * Occurrences of each tuple is counted and at the end of window any of the least frequent tuple is emitted on output port 'least'
 * All keys with same least frequency value least are emitted on output port 'list'.<br>
 * This module is an end of window module<br>
 * In case of a tie any of the least key would be emitted. The list port would however have all the tied keys
 * <br>
 * <b>StateFull : Yes, </b> tuple are compared across application window(s). <br>
 * <b>Partitions : Yes, </b> least keys are unified on output port. <br>
 * <br>
 * <b>Ports</b>:<br>
 * <b>data</b>: expects K<br>
 * <b>least</b>: emits HashMap&lt;K,Integer&gt;(1), Where K is the least occurring key in the window.
 *               In case of tie any of the least key would be emitted<br>
 * <b>list</b>: emits ArrayList&lt;HashMap&lt;K,Integer&gt;(1)&gt, Where the list includes all the keys that are least frequent<br>
 * <br>
 *
 * @since 0.3.3
 * @author Amol Kekre <amol@datatorrent.com>
 */
public class LeastFrequentKey<K> extends AbstractBaseFrequentKey<K>
{
  @InputPortFieldAnnotation(name = "data")
  public final transient DefaultInputPort<K> data = new DefaultInputPort<K>()
  {
    /**
     * Calls super.processTuple(tuple)
     */
    @Override
    public void process(K tuple)
    {
      processTuple(tuple);
    }
  };

  /**
   * Output port, optional.
   */
  @OutputPortFieldAnnotation(name = "least", optional=true)
  public final transient DefaultOutputPort<HashMap<K, Integer>> least = new DefaultOutputPort<HashMap<K, Integer>>()
  {
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Unifier<HashMap<K, Integer>> getUnifier()
    {
      LeastFrequentKeyUnifier ret = new LeastFrequentKeyUnifier<K>();
      return ret;
    }
  };

  /**
   * Output port.
   */
  @OutputPortFieldAnnotation(name = "list", optional=true)
  public final transient DefaultOutputPort<ArrayList<HashMap<K, Integer>>> list = new DefaultOutputPort<ArrayList<HashMap<K, Integer>>>()
  {
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Unifier<ArrayList<HashMap<K, Integer>>> getUnifier()
    {
      LeastFrequentKeyArrayUnifier ret = new LeastFrequentKeyArrayUnifier<K>();
      return ret;
    }
  };

  /**
   * Emits tuple on port "least"
   * @param tuple
   */
  @Override
  public void emitTuple(HashMap<K, Integer> tuple)
  {
    least.emit(tuple);
  }

  /**
   * returns val1 < val2
   * @param val1
   * @param val2
   * @return val1 < val2
   */
  @Override
    public boolean compareCount(int val1, int val2)
  {
    return val1 < val2;
  }

  /**
   * Emits tuple on port "list"
   * @param tlist
   */
  @Override
  public void emitList(ArrayList<HashMap<K, Integer>> tlist)
  {
    list.emit(tlist);
  }
}
