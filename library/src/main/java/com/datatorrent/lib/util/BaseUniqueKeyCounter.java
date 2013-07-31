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
package com.datatorrent.lib.util;

import java.util.HashMap;
import org.apache.commons.lang.mutable.MutableInt;

/**
 * Count unique occurrences of keys within a window
 *
 * @param <K>
 * @since 0.3.2
 * @author Chetan Narsude <chetan@datatorrent.com>
 */
public class BaseUniqueKeyCounter<K> extends BaseKeyOperator<K>
{
  /**
   * Reference counts each tuple
   *
   * @param tuple
   */
  public void processTuple(K tuple)
  {
    MutableInt i = map.get(tuple);
    if (i == null) {
      i = new MutableInt(0);
      map.put(cloneKey(tuple), i);
    }
    i.increment();
  }

  /**
   * Bucket counting mechanism.
   * Since we clear the bucket at the beginning of the window, we make this object transient.
   */
  protected HashMap<K, MutableInt> map = new HashMap<K, MutableInt>();
}
