/**
 * Copyright (c) 2015 DataTorrent, Inc.
 * All rights reserved.
 */
package com.datatorrent.lib.appdata.query.serde;

import com.datatorrent.lib.appdata.schemas.Message;

/**
 * This is the validator for {@link DataQueryDimensional} objects.
 */
public class DataQueryDimensionalValidator implements CustomMessageValidator
{
  /**
   * Constructor used to instantiate validator in {@link MessageDeserializerFactory}.
   */
  public DataQueryDimensionalValidator()
  {
  }

  @Override
  public boolean validate(Message query, Object context)
  {
    return true;
  }
}
