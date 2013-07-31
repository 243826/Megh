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

/**
 *
 * A single KeyValPair for basic data passing, It is a write once, and read often model. <p>
 * <br>
 * Key and Value are to be treated as immutable objects.
 *
 * @since 0.3.2
 * @author Amol Kekre <amol@datatorrent.com>
 */
public class HighLow
{
  protected Number high;
  protected Number low;

  /**
   * Added default constructor for deserializer.
   */
  public HighLow()
  {
    high = null;
    low = null;
  }

  /**
   * Constructor
   *
   * @param h
   * @param l
   */
  public HighLow(Number h, Number l)
  {
    high = h;
    low = l;
  }

  /**
   * @return high value
   */
  public Number getHigh()
  {
    return high;
  }

  /**
   *
   * @return low value
   */
  public Number getLow()
  {
    return low;
  }

  /**
   * @param h sets high value
   */
  public void setHigh(Number h)
  {
    high = h;
  }

  /**
   *
   * @param l sets low value
   */
  public void setLow(Number l)
  {
    low = l;
  }

  @Override
  public String toString()
  {
    return "(" + low.toString() + "," + high.toString() + ")";
  }

}
